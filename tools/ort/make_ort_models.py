"""Turn the fp32 ONNX exports into the fp16-weight ORT-format models the app bundles.

usage: make_ort_models.py <dir containing panels.onnx and bubbles.onnx>
"""
import os
import sys

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper, numpy_helper

MODELS = ("panels", "bubbles")
CONV_OPS = {"Conv", "FusedConv"}
CPU = ["CPUExecutionProvider"]


def optimise_offline(src, dst):
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED
    options.optimized_model_filepath = dst
    ort.InferenceSession(src, options, providers=CPU)


def conv_weights_to_fp16(src, dst):
    model = onnx.load(src)
    graph = model.graph
    initializers = {tensor.name: tensor for tensor in graph.initializer}
    weight_names = {node.input[1] for node in graph.node if node.op_type in CONV_OPS}
    casts = []
    for name in weight_names:
        tensor = initializers[name]
        graph.initializer.remove(tensor)
        graph.initializer.append(numpy_helper.from_array(numpy_helper.to_array(tensor).astype(np.float16), name + "_fp16"))
        casts.append(helper.make_node("Cast", [name + "_fp16"], [name], to=TensorProto.FLOAT))
    original_nodes = list(graph.node)
    del graph.node[:]
    graph.node.extend(casts + original_nodes)
    onnx.save(model, dst)
    return len(weight_names)


def save_as_ort_without_optimising(src, dst):
    options = ort.SessionOptions()
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
    options.optimized_model_filepath = dst
    options.add_session_config_entry("session.save_model_format", "ORT")
    ort.InferenceSession(src, options, providers=CPU)


def megabytes(path):
    return round(os.path.getsize(path) / 1e6, 1)


def convert(directory, name):
    source = f"{directory}/{name}.onnx"
    optimised = f"{directory}/{name}.optimised.onnx"
    fp16 = f"{directory}/{name}.fp16.onnx"
    target = f"{directory}/{name}.ort"
    optimise_offline(source, optimised)
    convs = conv_weights_to_fp16(optimised, fp16)
    save_as_ort_without_optimising(fp16, target)
    print(f"{name}: {convs} conv weights to fp16, {megabytes(source)} MB -> {megabytes(target)} MB")


if __name__ == "__main__":
    for model in MODELS:
        convert(sys.argv[1], model)
