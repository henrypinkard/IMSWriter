package HDF;
/**
 *
 * @author henrypinkard
 */
public class PipelineImage {
   
   public int channel, slice, frame;
   public Object pixels;
   public String time;
   public long[][] histograms;
   public String acqDate;
   
   public PipelineImage(Object pix, int chnl, int slce, int frm, String tme,  String date) {
      channel = chnl;
      slice = slce;
      frame = frm;
      pixels = pix;
      time = tme;
      acqDate = date;
   }
}
