# إعداد تجربة OpenPush مع Firebase حقيقي

هذا دليل تنفيذ تجربة فعلية، وليس سجل نجاح لها. حالة التجربة ونتائج الحالات الثلاث عشرة
في [التقرير العملي](PRACTICAL-TEST-REPORT.ar.md). لا تنتقل إلى لوحة التحكم قبل إثبات تجربة
Console ثم النقر ثم فتح HTTPS الصحيح على الجهاز.

## تجهيز المشروع والجهاز

1. افتح [Firebase Console](https://console.firebase.google.com/) واختر مشروع الاختبار الذي
   تملكه. لا تستخدم تطبيقًا إنتاجيًا أو جمهور مستخدمين فعليين لهذه التجربة.
2. أضف تطبيق Android بمعرّف **io.openpush.sample** كما هو في sample/build.gradle.
   يختلف هذا عن اسم المشروع وnamespace. تسجيل SHA ليس مطلوبًا لاختبار FCM الأساسي.
3. نزّل ملف **google-services.json** الخاص بهذا التطبيق وضعه في
   `C:\xampp\htdocs\OpenPush\sample\google-services.json`.
   لا تنزّل Service Account ولا تضع مفاتيح Admin في المشروع أو الهاتف. ملف إعداد العميل
   يحوي معرّفات إعداد عامة، ويختلف عن مفتاح إرسال خاص. الملف مستثنى من Git هنا.
4. Java وGradle وإضافة Google Services وMessaging مضبوطة بالفعل في هذا المثال.
   لا تنسخ إعدادات الجذر إلى مشروع مضيف قائم ولا تغيّر إصدارات Firebase الموجودة فيه.
5. وصّل جهازًا يدعم Google Play services، وفعّل Developer options ثم USB debugging،
   وافق على بصمة الكمبيوتر، وتحقق من ظهور الحالة `device` في `adb devices -l`.
   `unauthorized` أو `offline` ليستا جهازًا جاهزًا للاختبار.
6. استخدم JDK 17 وAndroid SDK Platform 35. من جذر المشروع:

```powershell
$env:JAVA_HOME = '<مسار JDK 17>'
$env:ANDROID_HOME = '<مسار Android SDK>'
.\gradlew.bat :sample:assembleDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r sample\build\outputs\apk\debug\sample-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n io.openpush.sample/.MainActivity
```

إذا كان هناك أكثر من جهاز فحدد `adb -s SERIAL` لكل أمر. لا تستخدم openpushLocalOnly أو
openpushCompileOnly لبناء تجربة Firebase؛ هذان الخياران يتجاوزان إعداد Google Services.
يفشل البناء الطبيعي عند غياب ملف العميل بدل اختراع إعداد غير حقيقي.

## داخل التطبيق

- تظهر حالة تهيئة Firebase، ثم توكن اختبار قابل للنسخ. نجاح إنشاء التوكن ليس دليلًا على
  وصول رسالة أو صحة التنقل. لا تنسخ التوكن إلى سجلات أو تقارير عامة.
- اضغط Enable notifications على Android 13+، وامنح الإذن. راجع إعدادات إشعارات التطبيق
  وقناة `openpush`؛ الإذن وحده لا يضمن أن القناة مفعلة.
- التطبيق يحوي خدمة MessagingService واحدة. الرسائل التي تحوي op_v تذهب إلى SDK.
  الإشعار العادي في المقدمة يعرضه HostMessageHandler، وهو سلوك مثال التطبيق المضيف،
  أما في الخلفية فيعرضه Firebase تلقائيًا. لا تضف خدمة منافسة.
- أزرار LOCAL تولد رسائل محلية اصطناعية. العداد LOCAL_SIMULATION منفصل عن FCM_CALLBACK.
  لا تعتبر ظهور إشعار محلي دليلًا على Firebase. وحتى FCM_CALLBACK هو عداد تنفيذ callback؛
  الاختبارات البرمجية قد تستدعيه مباشرة، لذلك لا يكفي وحده لإثبات النقل.

## تجربة Console الأساسية: Notification + Data ثم HTTPS

1. في مشروع Firebase افتح Messaging/Notifications composer وأنشئ إشعارًا بعنوان ونص.
2. في Advanced options / Custom data أدخل **كل مفتاح وقيمة في حقل مستقل**:

```json
{"op_v":"1","op_id":"console-A-UNIQUE","op_type":"url","op_target":"https://example.com/?openpush=qa-A"}
```

3. استخدم إرسال الاختبار إلى توكن الجهاز المحدد. أكمل Custom data قبل الإرسال؛ لا تعتمد
   على نافذة اختبار سريعة لم تُرفق بها هذه الحقول. لا تضبط click_action مخصصًا.
4. اترك التطبيق في المقدمة أولًا، أرسل الرسالة، تحقق من الإشعار، ثم انقر وتحقق من عنوان
   المتصفح كاملًا ومن وجود `openpush=qa-A`. سجّل الجهاز وAPI ومعرّف الحالة ونتيجتها،
   دون التوكن أو بيانات حساسة. العدّاد OPENED يجب أن يزداد مرة واحدة.
5. اضغط Home وأعد الإرسال بمعرّف جديد؛ توقع عرض Firebase في الخلفية ووصول البيانات
   إلى launcher عند النقر، لا إلى onMessageReceived. عند الفشل سجّل فشلًا/عدم وصول،
   ولا تعوّض هذه الخطوة بزر LOCAL.
6. قبل النقر على الإشعار A أرسل إشعار B بمعرّف جديد ورابط:

```json
{"op_v":"1","op_id":"console-B-UNIQUE","op_type":"url","op_target":"https://example.com/?openpush=qa-B"}
```

انقر B ثم A وتحقق من كل عنوان على حدة. لا تعِد استخدام op_id لحالة جديدة. FCM قد يطوي
رسائل Notification المعلقة؛ دوّن العدد الذي ظهر فعليًا ولا تفترض أن كل إرسال يحتفظ بإشعار.
افتح التطبيق وعد إلى نفس Intent باستخدام LOCAL: replay current click Intent؛ المتوقع DUPLICATE
دون فتح متصفح جديد. هذا فحص إعادة التنفيذ، ولا يُعد إرسالًا جديدًا من FCM.

## Deep Link ومعلمات المحتوى

```json
{"op_v":"1","op_id":"console-deep-UNIQUE","op_type":"deeplink","op_target":"openpush://article/read/42","op_arg_article_id":"42","op_arg_level":"3"}
```

بعد النقر يجب أن تظهر Article 42 وLevel: 3 داخل ArticleActivity المعلنة في Manifest.
جرّب وجهة غير مدعومة مثل openpush://missing/read/42، ونوعًا غير مسجل، ورابط javascript:؛
المتوقع عدم فتح وجهة غير مسموحة وعدم انهيار التطبيق. قد يعرض Firebase إشعارًا ذا بيانات
توجيه غير صالحة في الخلفية؛ رفض التوجيه يُفحص عند النقر.

## أنواع الرسائل وحالات التطبيق

- **Notification فقط:** في المقدمة يصل callback ويعرضه معالج المضيف في هذا المثال؛
  في الخلفية يعرضه Firebase، والنقر يفتح launcher دون توجيه OpenPush لغياب op_v.
- **Data فقط:** يصل إلى الخدمة وفق قيود النظام والتسليم؛ لا يعرضه FCM تلقائيًا. يختار
  التطبيق/SDK عرضه. Console composer لا يولد هذا النوع؛ تجربته الحقيقية تتطلب مرسل
  HTTP v1/Admin موثوقًا خارج Android، وليست جزءًا من خادم جديد في هذه المرحلة.
- **Notification + Data:** كلا الجزأين متاحان في المقدمة؛ في الخلفية يعرض FCM الإشعار
  وتصل Custom data مع Intent النقر. استدعاء callback اصطناعي لا يختبر هذا السلوك الخلفي.

افصل بين Home، وإعادة إنشاء الشاشة، وقتل العملية العادي، والسحب من recents، وForce stop
من Settings. سجّل كل حالة مستقلة. Force stop يتطلب فتح التطبيق يدويًا لاستئناف الاعتماد
على FCM. Android/OEM وDoze والشبكة وTTL والطي قد تؤثر في التسليم؛ لا تضمن المكتبة تجاوزه.

## فحص APK والأدلة قبل إغلاق المرحلة

```powershell
powershell -File scripts\audit-apk.ps1 -ApkPath sample\build\outputs\apk\debug\sample-debug.apk -ArtifactLabel 'real Firebase configured test APK' -OutputPath verification\apk-audit.configured.json
```

الفحص يسجل SHA-256 واسم الإدخال ونوع المؤشر فقط عند الاشتباه، دون طباعة قيمة مفتاح.
إنه فحص مؤشرات ثابتة لا يثبت غياب كل سر مشفر أو محمل وقت التشغيل. أعده على APK الحقيقي
المهيأ؛ نتيجة APK المحلي لا تنتقل تلقائيًا إلى APK آخر. لا تعامل Firebase client API key
كمفتاح إرسال خادم. احفظ دليل نتيجة Console والرابط الصحيح مع نسخة APK التي اختُبرت،
ثم حدّث حالة شرط الانتقال بعد التحقق الفعلي فقط.

المصادر الرسمية: [إعداد Android](https://firebase.google.com/docs/cloud-messaging/android/get-started)،
[الاستقبال](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)،
[أنواع الرسائل وCustom data](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type)،
[الإرسال من Console](https://firebase.google.com/docs/cloud-messaging/send/firebase-console).
# نتيجة التنفيذ الحالية

نُفذت هذه الخطوات على مشروع Firebase الحقيقي وهاتف RMX3852 / API 34. أكد المستخدم
النقر وفتح رابط A؛ تحقق الفحص الآلي من عنواني A4 وB4 في Opera عبر PendingIntent الحقيقي.
نجح النقر الآلي لإشعار المقال وأظهر Article 42 وLevel: 3. انظر تقرير
[النتائج والقيود](PRACTICAL-TEST-REPORT.ar.md) بدل افتراض نجاح كل حالة في هذا الدليل.

