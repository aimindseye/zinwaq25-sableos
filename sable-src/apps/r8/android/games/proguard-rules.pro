# No keep rules for the games themselves: since the Kotlin port the app has no native (JNI) code.

# Sable Games Console (console flavor) runs Lua with LuaJ. Its libraries name their functions by
# class, and luaj-jse refers to desktop-only classes (javax.script, Swing, BCEL) the console never
# loads; keep LuaJ's core and let those references go.
-keep class org.luaj.vm2.** { *; }
-dontwarn org.luaj.**
-dontwarn javax.script.**
-dontwarn org.apache.bcel.**
