# ============================================================================
# Checkout Card Management SDK Consumer ProGuard/R8 Rules
# These rules are bundled with the AAR and applied during app build time.
# ============================================================================

# Suppress warnings
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn com.thalesgroup.gemalto.d1.D1Task

# Keep public API entry point
-keep class com.checkout.cardmanagement.CheckoutCardManager { public *; }

# Keep all public model classes and their members (needed for data classes)
-keep class com.checkout.cardmanagement.model.** { *; }

# Keep sealed class subclasses (required for Kotlin sealed class reflection)
-keep class com.checkout.cardmanagement.model.CardManagementError$* { *; }

# Keep enum values
-keepclassmembers enum com.checkout.cardmanagement.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Kotlin metadata for SDK's public API (enables Kotlin features for consumers)
-keepattributes RuntimeVisibleAnnotations
-keep @kotlin.Metadata class com.checkout.cardmanagement.CheckoutCardManager
-keep @kotlin.Metadata class com.checkout.cardmanagement.model.**

# Keep Kotlin metadata + generic reflection-friendly attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep internal logging classes - they use kotlin-reflect to read member names
-keep class com.checkout.cardmanagement.logging.LogEvent { *; }
-keep class com.checkout.cardmanagement.logging.LogEvent$* { *; }
-keep class com.checkout.cardmanagement.logging.LogEventUtils { *; }
-keep class com.checkout.cardmanagement.logging.LogEventSource { *; }
-keep class com.checkout.cardmanagement.logging.CheckoutEventLogger { *; }

# Keep Kotlin Metadata class for reflection
-keep class kotlin.Metadata { *; }

# Keep TextStyle and related classes for kotlin-reflect access
# TextStyle delegates properties to SpanStyle/ParagraphStyle, so we need to keep all
-keep class androidx.compose.ui.text.TextStyle { *; }
-keep class androidx.compose.ui.text.SpanStyle { *; }
-keep class androidx.compose.ui.text.ParagraphStyle { *; }
-keepclassmembers class androidx.compose.ui.text.** {
    <fields>;
    <methods>;
}
