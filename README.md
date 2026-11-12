# Test App (Infinix Zero 30)

Simple Android app — screen par sirf ek text dikhata hai.

- Language: Kotlin
- minSdk 24, targetSdk 35 (Infinix Zero 30 = Android 13, chalega)
- Koi extra library nahi

## Run kaise karein
1. Android Studio mein **File > Open** karke `TestApp` folder kholein.
2. Gradle sync hone dein (pehli dafa internet chahiye).
3. Phone par **Developer options > USB debugging** ON karein, USB se connect karein.
4. Green **Run ▶** button dabayein.

## APK banana
**Build > Build App Bundle(s) / APK(s) > Build APK(s)**
APK yahan milegi: `app/build/outputs/apk/debug/app-debug.apk`

## Text badalna
`app/src/main/res/values/strings.xml` mein `hello_text` change karein.
