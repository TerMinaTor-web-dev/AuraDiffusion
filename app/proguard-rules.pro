# Proguard rules for AuraDiffusion
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-keep class com.auradiffusion.app.core.** { *; }
