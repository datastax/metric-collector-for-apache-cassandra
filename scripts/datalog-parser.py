#!/usr/bin/python

import struct
import glob
import sys
import gzip

for f in sys.argv[1:]:
    if f.endswith(".gz"):
        fin = gzip.open(f,'rb')    
    else:
        fin = open(f,'rb')

    while True:
        
        try:
            sz = struct.unpack('i', fin.read(4))[0]
        except:
            break
            
        if sz < 0 or sz > 1 << 20:
            raise Exception("Invalid length read from %s" % f)

        print fin.read(sz)

