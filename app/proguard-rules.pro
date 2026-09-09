# Project-specific R8 rules for production release builds.
# Android components referenced from the manifest and library consumer rules are
# retained automatically; avoid broad keep rules that would disable shrinking.

# Preserve useful crash diagnostics while hiding local source paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retain metadata used by AndroidX and reflection-based libraries.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
