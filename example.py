"""
This script was tested in Python 3.6
"""
from imariswriter import ImarisJavaWrapper
import numpy as np
import numpy.random as rand

directory = '/home/henry/'
name = 'ims_test'
shape =(1024, 1024, 20) #pixel dimensions
byte_depth = 1
num_channels = 2
num_frames = 3
pixel_size_xy_um = 0.5
pixel_size_z_um = 1.0

time_index = 0
channel_index = 0
z_index = 0
# writer = ImarisJavaWrapper(directory, name, shape, byte_depth, num_channels, num_frames, pixel_size_xy_um, pixel_size_z_um)

with ImarisJavaWrapper(directory, name, shape, byte_depth, num_channels, num_frames, pixel_size_xy_um, pixel_size_z_um) as writer:
	for time_index in range(num_frames):
	    for channel_index in range(num_channels):
	        for z_index in range(shape[2]):
	            #generate a random image for testing
	            #TODO: replace this with real data
	            tile = rand.randint(0, 240, shape[2], dtype=np.uint8)
	            #TODO: replace this with time image was taken in ms for accurate time calibration
	            elapsed_time_ms = time_index * 10000
	            print(time_index, channel_index, z_index)
	            writer.write_z_slice(tile, z_index, channel_index, time_index, elapsed_time_ms)
