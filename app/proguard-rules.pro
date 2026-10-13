# The Google Home APIs SDK doesn't publish keep rules, and finds device types and traits
# reflectively, so keep it whole. Has no effect on builds without the SDK.
-keep class com.google.home.** { *; }
-dontwarn com.google.home.**
