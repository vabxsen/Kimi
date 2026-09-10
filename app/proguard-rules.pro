# Keep readable stack traces from a minified build; without these a crash report is unusable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ThemeSetting persists this enum by constant name (`value.name`) and reads it back with
# valueOf, so the names must survive obfuscation - otherwise a stored preference written by
# one build cannot be parsed by the next. Everything else in the app is serialised through
# BackupCodec, which names every field explicitly and uses no reflection at all.
-keepclassmembers enum com.forma.habits.ThemeMode { *; }
