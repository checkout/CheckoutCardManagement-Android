# ============================================================================
# Checkout Card Management SDK ProGuard/R8 Rules
# Applied during library build time
# ============================================================================

# Suppress warnings for optional dependencies
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn com.thalesgroup.gemalto.d1.D1Task

# Keep public API during library build (scoped to pamir package)
-keepclasseswithmembers,allowoptimization public class com.checkout.cardmanagement.** {
    public <methods>;
    public <fields>;
}

-keepclasseswithmembers,allowoptimization public interface com.checkout.cardmanagement.** {
    public <methods>;
    public <fields>;
}

# Keep BuildConfig
-keep public class com.checkout.cardmanagement.BuildConfig { *; }

# Keep the actual public SDK API (avoid keeping Kotlin 'internal' implementation)
-keep class com.checkout.cardmanagement.CheckoutCardManager { public *; }
-keep class com.checkout.cardmanagement.model.** { public *; }

# BuildConfig is referenced at runtime
-keep class com.checkout.cardmanagement.BuildConfig { *; }

# Internal logging uses kotlin-reflect + string member names; don't obfuscate these
-keep class com.checkout.cardmanagement.logging.LogEvent { *; }
-keep class com.checkout.cardmanagement.logging.LogEvent$* { *; }
-keep class com.checkout.cardmanagement.logging.LogEventUtils { *; }
-keep class com.checkout.cardmanagement.logging.LogEventSource { *; }