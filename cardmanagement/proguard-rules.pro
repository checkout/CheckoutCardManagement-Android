-keepclasseswithmembers,allowoptimization public class * {
    public <methods>;
    public <fields>;
}

-keepclasseswithmembers,allowoptimization public interface * {
    public <methods>;
    public <fields>;
}

-keep,allowoptimization public class * {
    public <methods>;
    public <fields>;
}

-keep public class com.checkout.cardmanagement.BuildConfig { *; }

-repackageclasses com.checkout.cardmanagement

-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn com.thalesgroup.gemalto.d1.D1Task