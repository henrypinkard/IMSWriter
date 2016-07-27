package HDF;

import java.awt.Color;
import java.util.LinkedList;

/*
 * Class to encapsulate preprocessing and writing. preprocessing occurs on calling thread
 * and a designated thread for writing
 */
public class ImarisWriter {
   
      private static final int SLICES_FIRST = 1;
      private static final int CHANNELS_FIRST = 2;
   
      private volatile LinkedList<PipelineImage> writingQueue_, preprocessQueue_;
      private volatile boolean preprocessFinished_ = false;
      private Thread writingThread_;
      private int numSlices_, numChannels_;
      private int imageOrder_ = 0;
      private HDFWriter writer_;
      private HDFPreprocessor preprocessor_;
      private int slicesPerWrite_;    
     
   public ImarisWriter(String dir, String name, int width, int height, int numSlices, 
           int numChannels, int numFrames, double pixelSizeXY, double pixelSizeZ, Color[] channelColors) {
      ResolutionLevel[] resLevels = ResolutionLevelMaker.calcLevels(width, height,
              numSlices, numFrames, 1);
      preprocessor_ = new HDFPreprocessor(width, height, resLevels);

      writer_ = new HDFWriter(dir, name + ".ims", numChannels,
              numFrames, numSlices,
              pixelSizeXY, pixelSizeZ,
              channelColors, width, height, resLevels);
      slicesPerWrite_ = resLevels[resLevels.length - 1].getReductionFactorZ();
      numSlices_ = numSlices;
      numChannels_ = numChannels;
      writingQueue_ = new LinkedList<PipelineImage>();
      preprocessQueue_ = new LinkedList<PipelineImage>();
      writingThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            imarisWriting();
         } 
      });
      writingThread_.start();
   }
   
   /*
    * Can accept images in either channels first or slices first order
    * First image must supply the date
    */
   public void addImage(Object pixels, int slice, int channel, int frame, String date, String time) {
      //figure out ordering
      if (imageOrder_ == 0 && slice == 1) {
         imageOrder_ = SLICES_FIRST;
      } else if (imageOrder_ == 0 && channel == 1) {
         imageOrder_ = CHANNELS_FIRST;
      }

      //add to preprocess queue
      preprocessQueue_.add(new PipelineImage( pixels, channel, slice, frame, time, date ));
      
      //add dummy slices  if needed after the last slice in the stack
      if (slice == numSlices_ - 1 && slicesPerWrite_ > 1) {
         addDummySlices(slice, frame, channel);   
      }
      

      //wait until enough images in queue for preprocessing
      if ( (imageOrder_ == SLICES_FIRST && preprocessQueue_.size() == slicesPerWrite_) ||
             (imageOrder_ == 0 && slicesPerWrite_ == 1)) { //happens on first slice when slices per write is 1    
         //preprocess batch of slices in single channel to writer
         PipelineImage pi = preprocessor_.process(preprocessQueue_);
         preprocessQueue_.clear();
         synchronized (writingQueue_) {
            writingQueue_.add(pi);
         }
      } else if ((imageOrder_ == CHANNELS_FIRST) && preprocessQueue_.size() == slicesPerWrite_ * numChannels_) {   
         //preprocess batch of slices in each channel to writer     
         for (int c = 0; c < numChannels_; c++) {
            LinkedList<PipelineImage> singleChannelBatch = new LinkedList<PipelineImage>();
            for (int s = 0; s < slicesPerWrite_; s++) {
               singleChannelBatch.add(preprocessQueue_.get(s * numChannels_ + c));
            }
            PipelineImage pi = preprocessor_.process(singleChannelBatch);
            synchronized (writingQueue_) {
               writingQueue_.add(pi);
            }
         }
         preprocessQueue_.clear();
      }

      
      //wait until writer is caught up to return
      int size = 0;
      synchronized (writingQueue_) {
         size = writingQueue_.size();
      }
      while (size > 2) {
         try {
            Thread.sleep(10);
            synchronized (writingQueue_) {
               size = writingQueue_.size();
            }
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }
   }

   public void close() {
      preprocessFinished_ = true;
      try {
         writingThread_.join();
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
      writer_.close();
   }
   
   private void addDummySlices(int sliceIndex, int frameIndex, int channelIndex) {
      //Last slice for this time point in this channel-send dummy images as needed
      if (imageOrder_ == CHANNELS_FIRST) {
         //add full complement of dummy images for each channel
         for (int s = sliceIndex + 1; s % slicesPerWrite_ != 0; s++) {
            for (int c = 0; c < numChannels_; c++) {
               preprocessQueue_.add(new PipelineImage(null, c, s, frameIndex, null, null));
            }
         }
      } else {
         //add dummy images for this channel only
         for (int s = sliceIndex + 1; s % slicesPerWrite_ != 0; s++) {
            preprocessQueue_.add(new PipelineImage(null, channelIndex, s, frameIndex, null, null));
         }
      }
   }

   private void imarisWriting() {
      while (true) {
         PipelineImage toWrite = null;
         synchronized (writingQueue_) {
            if (!writingQueue_.isEmpty()) {
               toWrite = writingQueue_.removeFirst();
            }
         }

         if (toWrite == null) {
            if (preprocessFinished_) {
               break;
            }
            try {
               Thread.sleep(10);
            } catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         } else {
            try {
               writer_.writeImage(toWrite);
            } catch (Exception ex) {
               ex.printStackTrace();
            }
         }
      }

   }
   
   
}
