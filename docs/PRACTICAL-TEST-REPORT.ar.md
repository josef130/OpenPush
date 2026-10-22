# تقرير الاختبار العملي لـOpenPush

تاريخ الفحص: 2026-10-17. نُفذ الإرسال الفعلي من Firebase Console إلى هاتف RMX3852
بإصدار Android 14 (API 34). أكد المستخدم النقر وفتح رابط A الصحيح، وسجل التطبيق OPENED.
هذه مشاهدة المستخدم للنقر الحقيقي. نجح أيضًا فحص عنوان المتصفح آليًا لـA4 وB4 بتشغيل
PendingIntent الحقيقي لكل إشعار، وهي آلية منفصلة عن إيماءة الشريط. تحقق الشرط الأدنى
Console → HTTPS؛ الاعتماد الشامل غير مكتمل. لم يبدأ تطوير خادم أو لوحة تحكم.

## الأدلة المتاحة

- تحقق ملف sample/google-services.json الحقيقي: عميل Android واحد مطابق لـio.openpush.sample،
  دون حقول Service Account. لم تُطبع قيم API key أو إعدادات العميل في السجل.
- بُني APK طبيعي مع تنفيذ processDebugGoogleServices، دون خيار الوضع المحلي.
  نسخة الاختبار محفوظة في build/practical-apks/OpenPush-firebase-debug.apk.
- اجتازت النواة 49 فحصًا. توجد 13 حالة Robolectric للمكتبة و8 حالات تكامل للمثال.
  تقرير XML/HTML هو مرجع نتائج التنفيذ، وليس وصف السيناريو وحده.
- فحص APK المهيأ بـFirebase: **128 إدخال ZIP، صفر مؤشرات مطابقة لقواعد مفاتيح الخادم**.
  SHA-256: `1ef157d11ea9730e861a82070ed3814de63cb6f2cb47d7237d1ee13212121447`.
  التفاصيل في verification/apk-audit.configured.json. الفحص محدود بمؤشرات ثابتة؛ لا يثبت
  غياب كل سر مشفر/مشفر ترميزيًا أو محمّل وقت التشغيل.
- نجح اختبار أداة الفحص على مؤشر مفتاح خاص اصطناعي وعلى معرّفات Firebase عميل غير سرية.
- ثُبت APK المهيأ بـFirebase وشُغل على الهاتف، ونجح الحصول على توكن FCM. فعّل المستخدم
  POST_NOTIFICATIONS؛ أكد القياس notificationsEnabled=true وقناة OpenPush بأهمية 3.
  لم تُحفظ التوكنات في أدلة التحقق. دخل المستخدم إلى Console وأُرسلت رسائل اختبار إلى
  الهاتف المحدد فقط دون نشر حملة عامة.
- فشل تثبيت حزمة SDK الاختبارية المستقلة برفض OEM (INSTALL_FAILED_VERIFICATION_FAILURE).
  أُعيد استخدام الاختبارات نفسها داخل حزمة اختبار المثال ذات targetSdk الحديث دون تغيير
  targetSdk للتطبيق المستضيف: **نجحت الاختبارات الخمسة فعليًا**، انظر verification/device-sdk-contracts.txt.
- نجح اختبار Console للمقال آليًا عبر النقر في شريط Android: Article 42 وLevel: 3
  داخل io.openpush.sample. الدليل: verification/device-console-article42.json.
- نجح التشغيل النهائي المجمع لاختبارات الجهاز السبعة: خمسة عقود SDK وحالتان للمثال.
  الدليل verification/device-contracts.txt، بأداة scripts/test-device.ps1 التي ترفض فشل JUnit
  حتى لو أعاد adb رمز خروج صفر. أُصلح انتظار بدء Activity وانتظار ظهور إشعارات Android
  بعد فشل المحاولات الأولى؛ لا تُعد المحاولات الفاشلة نجاحًا.
- أثبت verification/device-outstanding-A3-B3.json بقاء إشعارين حقيقيين من Console؛
  B3 عرضه FCM في الخلفية، وA3 عرضه SDK في المقدمة. بقي FCM_CALLBACK=2 عند B3 ثم أصبح 3 عند A3.
- تعذر المحاكي سابقًا بسبب الذاكرة؛ استُخدم الهاتف بدلًا منه، ولم تُخفف إعدادات أمانه.
- فشلت محاولة إعادة الاختبارات بسبب native JVM out-of-memory قبل تنفيذها؛ أُعيدت
  المحاولة بحد Gradle قدره 768 MB وعامل واحد: نجحت 13 حالة SDK و8 حالات تكامل للمثال
  دون أخطاء أو حالات متروكة. ليست نتائج تسليم FCM.

## الحالات المطلوبة ونتيجة كل حالة

1. **إشعار عادي في شريط الإشعارات**
   - المحاكاة: ناجحة في ordinaryNotificationUsesExistingHostHandlerAndOpensHome؛ يعرضه
     معالج المضيف، والنقر يفتح MainActivity دون تنفيذ توجيه OpenPush.
   - فعليًا: نجح Notification-only في المقدمة بمعالج المضيف، وبعد إنهاء العملية العادي
     بعرض FCM. الدليلان device-notification-only-foreground.json وdevice-notification-only-cold.json.

2. **Notification + Custom Data**
   - المحاكاة: ناجحة؛ Fixture له Notification وData، يمر إلى SDK ولا ينفذ الوجهة قبل النقر.
   - فعليًا: ناجح في المقدمة والخلفية؛ فتحت بيانات المقال الوجهة والمعلمات الصحيحة.

3. **فتح رابط HTTPS عند النقر**
   - المحاكاة: ناجحة عبر PendingIntent ثم onCreate الحقيقي للمثال؛ التُقط ACTION_VIEW
     مع https://example.com/?openpush=qa-A.
   - فعليًا: أكد المستخدم فتح https://example.com/?openpush=qa-A بعد النقر على إشعار Console.
     الاختبار الآلي لـA3 وB3 فشل في قراءة عنوان المتصفح: بقيت الحزمة المرصودة com.android.systemui
     والعنوان فارغًا. لا يثبت ذلك فتح رابط خاطئ ولا يُعد نجاحًا آليًا. حُفظ فشل B3 في
     verification/device-console-B3-tap.json.
   - نجح لاحقًا تشغيل PendingIntent الفعلي لإشعار A4: قرئ عنوان Opera كاملًا مطابقًا.
     الدليل device-console-A4-pending-intent.json؛ هذا ليس نجاحًا لإيماءة الشريط الآلية.

4. **تغيير الرابط بين إشعارين**
   - المحاكاة: ناجحة في multipleOutstandingNotificationsKeepChangedUrlsAndWarmEntryCorrect؛
     إرسال Fixtures لـA وB، والنقر B ثم A عبر onNewIntent يعطي qa-B ثم qa-A.
   - فعليًا: نجح تشغيل PendingIntent لإشعارَي A4 وB4 الحقيقيين وقراءة عنواني Opera:
     https://example.com/?openpush=qa-A4 ثم https://example.com/?openpush=qa-B4.
     الدليلان device-console-A4-pending-intent.json وdevice-console-B4-pending-intent.json.
     يثبت دليل B4 بقاء الإشعارين أيضًا. لم ينجح اختبار إيماءتي الشريط الآليتين لـA3/B3.

5. **Deep Link داخلي مدعوم**
   - المحاكاة: ناجحة باستخدام Manifest المثال نفسه، دون استبدال نتيجة حل الوجهة بFixture؛
     تُفتح ArticleActivity المعلنة عبر openpush://article/read/42.
   - فعليًا: نجح Deep Link المعلن للمقال عبر إشعار Console ونقر Android آلي.
     App Links الموثقة بالنطاق ما زالت غير مختبرة.

6. **معرّف المحتوى والمعلمات**
   - المحاكاة: ناجحة؛ فُحصت extras article_id=42 وlevel=3، ثم أُنشئت ArticleActivity
     وفُحص النص الظاهر Article 42 وLevel: 3. لا تُمرر op_v إلى الوجهة لإعادة توجيه متكررة.
   - فعليًا: نجح إشعار Console؛ ظهر النص Article 42 وLevel: 3 في Activity المقصودة.

7. **المقدمة والخلفية وإغلاق التطبيق**
   - المحاكاة: ناجحة لمسار callback المباشر، وonCreate، وonNewIntent، وإعادة إنشاء Activity.
     ذلك لا يختبر عملية تسليم FCM ولا إعادة تشغيل عملية Android بعد قتلها.
   - فعليًا: نجحت المقدمة والخلفية لإشعارات Notification + Data؛ خدمة المضيف تتلقى في
     المقدمة، وFCM يعرض في الخلفية. نجح Notification-only بعد am kill وغياب PID قبل الإرسال.
     لا يعادل ذلك Force stop؛ recents وForce stop لم يُختبرا.

8. **رسالة جديدة مع إشعارات سابقة**
   - المحاكاة: ناجحة؛ بقيت ثلاثة إشعارات مستقلة، وبقيت بيانات كل PendingIntent صحيحة.
     لا تحاكي PendingIntent.send إيماءة Android التي تلغي إشعار autoCancel تلقائيًا.
   - فعليًا: ثبت وجود A3 وB3 معًا في شريط Android مع PendingIntent لكل منهما وملخص OEM.
     لا تعمم النتيجة على تسليم الرسائل أثناء offline أو سياسة collapse وترتيبها.

9. **منع التنفيذ المكرر**
   - المحاكاة: ناجحة؛ OPENED يزداد مرة واحدة، وإعادة Intent وإعادة إنشاء MainActivity
     تعطي DUPLICATE دون طلب فتح وجهة ثانية. اختُبر أيضًا سجل 128 معرّفًا وفشل التخزين بالنواة.
   - فعليًا: نجحت خمسة عقود SDK وفتح screen مرة ثم تكرار onNewIntent مع ثبات OPENED
     وزيادة DUPLICATE. دخل الاختبار عبر Activity فعلية ببيانات اصطناعية، وليس إعادة تسليم
     FCM بعيدة. موت العملية بين claim والتنقل غير مختبر. الضمان محدود بسجل محفوظ؛ الانهيار بعد
     حفظ المعرف وقبل التنقل قد يفقد التنقل، ولا يوجد ضمان exactly-once لإكمال الوجهة.

10. **الرابط غير الصالح والوجهة غير المدعومة**
    - المحاكاة: ناجحة؛ javascript: ونطاق غير مسموح وDeep Link غير مسجل وإجراء مجهول
      تُرفض دون طلب Activity أو انهيار. اختُبر غياب المتصفح وفشل المعالج أيضًا.
    - فعليًا على Android: نجح رفض javascript: وإجراء unsupported عبر onNewIntent للمثال
      مع REJECTED +2 ودون فتح وجهة. هذه Intents اصطناعية وليست رسائل FCM بعيدة غير صالحة.

11. **مضيف لديه FirebaseMessagingService مسبقًا**
    - المحاكاة: ناجحة على خدمة المثال؛ الرسائل غير التابعة للمكتبة ومعالجة التوكن والحذف
      تحتفظ بعداداتها، وتوجد خدمة مضيف واحدة في Manifest. خدمة Firebase الاحتياطية طبيعية.
    - هذا Fixture لمضيف، وليس إثبات توافق كل تطبيق إنتاجي أو كل SDK إشعارات آخر.
      نجح callback فعلي في خدمة المثال: SDK_OWNED لرسالة المكتبة، وHOST_DISPLAYED للإشعار
      العادي؛ تحقق TOKEN_CALLBACK فعليًا. Data غير التابعة اختُبرت محليًا على الهاتف.
      onDeletedMessages الحقيقي ومضيف إنتاجي خارجي غير مختبرين.

12. **الإذن والقنوات**
    - المحاكاة: ناجحة لمنح/رفض POST_NOTIFICATIONS على API 33، وتعطيل القناة، والمحافظة
      على اسم القناة الذي اختاره المضيف. Manifest الحقيقي يحتوي إذن الإشعارات.
    - فعليًا: رُصد الرفض أولًا ثم منح المستخدم الإذن، وتأكد وجود القناة بأهمية 3.
      تعطيل القناة وإلغاء الإذن من إعدادات الهاتف لم يُختبرا يدويًا.

13. **غياب مفاتيح خادم في APK**
    - نُفذ فحص محتويات APK الحقيقي المبني: صفر مؤشرات مطابقة، مع حفظ بصمته والتقرير.
      لم تُضف Service Account أو مفاتيح إرسال إلى مصادر التطبيق.
    - هذا فحص ثابت محدود القواعد، وليس برهانًا شاملًا على غياب أي سر بأي تمثيل.
      أعد الفحص على أي APK يتغير أو يصدر لاحقًا؛ Firebase client API key ليس مفتاح خادم.

## اختلاف أنواع الرسائل: توقعات موثقة وليست نتائج تسليم

- Notification فقط: callback في المقدمة، وعرض FCM في الخلفية. مثالنا يعرض الإشعار
  العادي في المقدمة بمعالج المضيف.
- Data فقط: يعالجه التطبيق ولا يعرضه FCM تلقائيًا؛ القيود والأولوية تؤثر في التسليم.
  لم يُرسل Data-only حقيقي. Console composer لا ينشئ هذا النوع.
- Notification + Data: callback يحمل الجزأين في المقدمة؛ في الخلفية يعرض FCM الإشعار
  وتأتي البيانات مع Intent النقر، دون callback خدمة المضيف.

المراجع: [الاستقبال](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)،
[أنواع الرسائل](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type).
استدعاءات الاختبار الاصطناعية لا تُثبت اختلافات النقل الخلفي هذه.

## استكمال الاختبار ورفع شرط التوقف

اتبع [دليل Firebase العملي](FIREBASE-PRACTICAL-SETUP.ar.md). تحتاج جلسة Console مسجلة
الدخول وجهازًا online يدعم Google Play services. سجّل جهاز الاختبار وAPI وبصمة APK
ومعرّف الحالة والوجهة المتوقعة والمشاهدة ونتيجة النقر، دون توكن أو أسرار.
حُدث verification/phase-gate.json بالأدلة: الشرط الأدنى ناجح، والاعتماد الشامل غير مكتمل.
Data-only الحقيقي يتطلب مرسلًا موثوقًا خارج Console دون أسرار في APK. تبقى إيماءات HTTPS
الآلية على OEM وForce stop وإلغاء الإذن/القناة وأجهزة/API إضافية ضمن العمل المتبقي.
لم تُبنَ لوحة تحكم أو خادم خلال هذا الفحص.
