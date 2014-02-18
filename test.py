


import pipes
t = pipes.Template()
t.append('osra testimage.png', '--')
f = t.open('pipefile', 'w')
f.close()
if open('pipefile').read() == "CccC":
      print "Passed"
else
      print "Failed"
