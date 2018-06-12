package HDF;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import javax.swing.JOptionPane;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

/*
 * Class to encapsulate preprocessing and writing. preprocessing occurs on calling thread
 * and a designated thread for writing
 */
public class ImarisWriter {

   private static final int SLICES_FIRST = 1;
   private static final int CHANNELS_FIRST = 2;

   private volatile LinkedBlockingDeque<PipelineImage> writingQueue_, preprocessQueue_;
   private final int numSlices_, numChannels_;
   private volatile int imageOrder_ = 0;
   private HDFWriter writer_;
   private HDFPreprocessor preprocessor_;
   private int slicesPerWrite_;
   private int width_;
   private int height_;
   private ExecutorService preprocessExecutor_, writingExecutor_;

   //for testing
   public static void main(String[] args) throws InterruptedException, HDF5Exception {
      int width = 400, height = 400;
      int slices = 1, channels = 1, frames = 2;
      int byteDepth = 2;
      ImarisWriter writer = new ImarisWriter("/Users/henrypinkard/Desktop/", "ImsTest", width, height, byteDepth, slices, channels, frames, 1.0, 1.0);
      for (int timeIndex = 0; timeIndex < frames; timeIndex++) {
         for (int sliceIndex = 0; sliceIndex < slices; sliceIndex++) {
            byte[] pix = new byte[2*width * height];
            writer.addImage(pix, sliceIndex, 0, timeIndex, 0);
         }
      }
      writer.close();
   }

   public ImarisWriter(String dir, String name, int width, int height, int byteDepth, int numSlices,
           int numChannels, int numFrames, double pixelSizeXY, double pixelSizeZ) {
      this(dir, name, width, height, byteDepth, numSlices, numChannels, numFrames, pixelSizeXY, pixelSizeZ, null);
   }

   public ImarisWriter(String dir, String name, int width, int height, int byteDepth, int numSlices,
           int numChannels, int numFrames, double pixelSizeXY, double pixelSizeZ, Color[] channelColors) {
      ResolutionLevel[] resLevels = ResolutionLevelMaker.calcLevels(width, height,
              numSlices, numFrames, byteDepth);
      preprocessor_ = new HDFPreprocessor(width, height, resLevels, 8 * byteDepth);
      width_ = width;
      height_ = height;
      writer_ = new HDFWriter(dir, name + ".ims", numChannels,
              numFrames, numSlices, byteDepth,
              pixelSizeXY, pixelSizeZ,
              channelColors, width, height, resLevels);
      slicesPerWrite_ = resLevels[resLevels.length - 1].getReductionFactorZ();
      numSlices_ = numSlices;
      numChannels_ = numChannels;
      writingQueue_ = new LinkedBlockingDeque<PipelineImage>();
      preprocessQueue_ = new LinkedBlockingDeque<PipelineImage>();
      preprocessExecutor_ = Executors.newSingleThreadExecutor();
      writingExecutor_ = Executors.newSingleThreadExecutor();
   }

   /**
    * Get the number of slices that are in the queue but have yet to be written,
    * in order to be able to limit RAM usage from python
    */
   public int getNumSlicesInQueue() {
      return preprocessQueue_.size() + writingQueue_.size() * slicesPerWrite_;
   }
   
   /**
    * easier signature that calculates and formats the date
    */
   public void addImage(Object pixels, int slice, int channel, int frame, int time_ms) {
      int ms = time_ms % 1000;
      int s = time_ms / 1000;
      int min = s / 60;
      int h = min / 60;
      DecimalFormat format = new DecimalFormat("00");     
      DecimalFormat format3 = new DecimalFormat("00");
      String timeString = "2018-06-01 "+format.format(h)+":"+format.format(min)+":"+format.format(s)+"."+format3.format(ms);
      this.addImage(pixels, slice, channel, frame, "", timeString);
   }

   /*
    * Can accept images in either channels first or slices first order
    * First image must supply the date
    */
   public void addImage(final Object pixels, final int slice, final int channel, 
           final int frame, final String date, final String time) {
      preprocessExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            try {
               //figure out ordering
               if (imageOrder_ == 0 && slice == 1) {
                  imageOrder_ = SLICES_FIRST;
               } else if (imageOrder_ == 0 && channel == 1) {
                  imageOrder_ = CHANNELS_FIRST;
               }

               //convert byte array passed in by python to short array
               //if its supposed to representing 16 bit pixels
               Object newPix;
               if (pixels instanceof byte[] && width_ * height_ * 2 == ((byte[]) pixels).length) {
                  ShortBuffer sb = ByteBuffer.wrap(((byte[]) pixels)).order(ByteOrder.nativeOrder()).asShortBuffer();
                  newPix = new short[sb.limit()];
                  sb.get((short[]) newPix);
               } else {
                  newPix = pixels;
               }

               //add to preprocess queue
               preprocessQueue_.add(new PipelineImage(newPix, channel, slice, frame, time, date));

               //add dummy slices  if needed after the last slice in the stack
               if (slice == numSlices_ - 1 && slicesPerWrite_ > 1) {
                  addDummySlices(slice, frame, channel);
               }

               //wait until enough images in queue for preprocessing
               if ((imageOrder_ == SLICES_FIRST && preprocessQueue_.size() == slicesPerWrite_)
                       || (imageOrder_ == 0 && slicesPerWrite_ == 1)) { //happens on first slice when slices per write is 1    
                  //preprocess batch of slices in single channel to writer
                  LinkedList<PipelineImage> batch = new LinkedList<PipelineImage>();
                  preprocessQueue_.drainTo(batch, slicesPerWrite_);
                  PipelineImage pi = preprocessor_.process(batch); //This is where most of the processing happens
                  writingQueue_.addLast(pi);
                  writingExecutor_.submit(imageWritingTask());
               } else if ((imageOrder_ == CHANNELS_FIRST) && preprocessQueue_.size() == slicesPerWrite_ * numChannels_) {
                  //preprocess batch of slices in each channel to writer     
                  LinkedList<PipelineImage> allChannels = new LinkedList<PipelineImage>();
                  preprocessQueue_.drainTo(allChannels, numChannels_ * slicesPerWrite_);
                  for (int c = 0; c < numChannels_; c++) {
                     LinkedList<PipelineImage> singleChannelBatch = new LinkedList<PipelineImage>();
                     for (int s = 0; s < slicesPerWrite_; s++) {
                        singleChannelBatch.add(allChannels.get(s * numChannels_ + c));
                     }
                     PipelineImage pi = preprocessor_.process(singleChannelBatch);
                     writingQueue_.addLast(pi);
                     writingExecutor_.submit(imageWritingTask());
                  }
               }

            } catch (Exception e) {
              JOptionPane.showMessageDialog(null, e.toString());
              //show stack trace               
              StringWriter sw = new StringWriter();
               PrintWriter pw = new PrintWriter(sw);
               e.printStackTrace(pw);
               JOptionPane.showMessageDialog(null, sw.toString());
            }
         }
      });
   }

   public void close() throws HDF5Exception {
      try {
         preprocessExecutor_.shutdown();
         while (!preprocessExecutor_.isTerminated()) {
            Thread.sleep(5);
         }
         writingExecutor_.shutdown();
         while (!writingExecutor_.isTerminated()) {
            Thread.sleep(5);
         }
         writer_.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private Runnable imageWritingTask() {
      return new Runnable() {
         @Override
         public void run() {
            try {
               PipelineImage pi = null;
               while (pi == null) {
                  pi = writingQueue_.pollFirst();
                  Thread.sleep(1);
               }
               writer_.writeImage(pi);
            } catch (Exception e) {
               StringWriter sw = new StringWriter();
               PrintWriter pw = new PrintWriter(sw);
               e.printStackTrace(pw);
               JOptionPane.showMessageDialog(null, sw.toString());
            }
         }
      };
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
}
