# 7-Zip-JBinding resolves these classes and members from native JNI_OnLoad by
# name, so R8 must not rename or strip them (crashes at library load otherwise).
-keep class net.sf.sevenzipjbinding.** { *; }
-keep interface net.sf.sevenzipjbinding.** { *; }
-dontwarn net.sf.sevenzipjbinding.**

# ONNX Runtime's JNI layer looks these classes and members up by name too;
# the locally built minimal AAR (tools/ort) ships no consumer rules.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
