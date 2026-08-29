import sys,os,numpy as np,onnx
from onnx import numpy_helper as nh, helper as h, TensorProto
src,dst=sys.argv[1],sys.argv[2]
m=onnx.load(src); g=m.graph
inits={t.name:t for t in g.initializer}
conv_w={n.input[1] for n in g.node if n.op_type=='Conv'}
new=[]
for name in conv_w:
    t=inits[name]; w=nh.to_array(t)
    g.initializer.remove(t); g.initializer.append(nh.from_array(w.astype(np.float16),name+'_h'))
    new.append(h.make_node('Cast',[name+'_h'],[name],to=TensorProto.FLOAT))
old=list(g.node); del g.node[:]; g.node.extend(new+old)
onnx.checker.check_model(m); onnx.save(m,dst); print('convs',len(conv_w),'MB',round(os.path.getsize(dst)/1e6,1))
