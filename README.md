# IMSWriter
Java Library for writing Imaris IMS files  

Matlab example for using library to write IMS file:  

directory = '/Users/henrypinkard/example/';  

%make sure the matlab librarypath.txt file is set correctly for JNI calls  
javaaddpath ImarisWriter.jar  
javaaddpath /Applications/HDF-JAVA.app/Contents/Java/jarhdf-2.10.0.jar  
javaaddpath /Applications/HDF-JAVA.app/Contents/Java/jarhdf5-2.10.0.jar  


pixelSizeZ = 2.0;  
pixelSizeXY = 1.0;  
slices = 10; %number of z slices  
frames = 1; %number of time points  
channels = 1;  
colors = [];  
width = 512;  
height = 512;  
prefix = 'Test ims file';  
%open writer  
imarisWriter = HDF.ImarisWriter(directory,prefix,width,height,slices,channels,frames,pixelSizeXY,pixelSizeZ,colors);  

Object pixels, int slice, int channel, int frame, String date, String time)   
%write single slice at a time  
pixels = zeros(width, height);  

%adjust as appropriate for each slice  
slice = 0;  
channel = 0;  
frame = 0;  
date = [];  
time = [];  
imarisWriter.addImage(pixels,slice,channel,frame,date,time);  

%close after all slices written  
imarisWriter.close();  
