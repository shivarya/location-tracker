# kotlinx.serialization: keep generated serializers for our @Serializable types (export/backup JSON, legacy import,
# type-safe navigation routes).
-keepclassmembers @kotlinx.serialization.Serializable class com.shivarya.locationspeedtracker.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room (ours and WorkManager's) instantiates databases reflectively via the generated *_Impl no-arg
# constructor; R8 stripped it in expense-tracker's release build (crash on launch).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
