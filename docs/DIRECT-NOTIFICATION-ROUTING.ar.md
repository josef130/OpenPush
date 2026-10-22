# التوجيه المباشر من الإشعارات

- روابط Google Play الموثقة أو اسم الحزمة الصحيح: Intent ACTION_VIEW للصفحة المحددة مع setPackage("com.android.vending"). لا يستخدم اختيار المتصفح عند نجاح المتجر.
- الويب: المتصفح وفق قواعد SDK وإعدادات Android. عند عدم اختيار متصفح افتراضي قد يعرض Android نافذة الاختيار.
- دون وجهة: يفتح التطبيق.
- Deep Link معلن ومعتمد: الشاشة المسجلة داخل التطبيق. إجراءات screen أو custom تظل تمر عبر المضيف للحفاظ على callbacks الحالية.

## منع ظهور واجهة البداية للروابط الخارجية

أضيفت OpenPushClickActivity بواجهة Theme.NoDisplay وتُغلق قبل الاستئناف. تستقبل نقرة SDK وتستخدم Router نفسه وClickLedger نفسه وإيصالات النقر نفسها، دون بناء MainActivity أولًا. تظل بحاجة إلى Application.onCreate لتهيئة نسخة SDK الواحدة قبل استقبال النقر، كما في SampleApplication. إذا لم تتم التهيئة أو تعذرت الوجهة يعود إلى التطبيق بأمان.

الإشعارات المعروضة بواسطة SDK تستخدم Activity النقر للروابط الخارجية والداخلية. رسائل Dashboard من نوع Notification + Data ذات الوجهة تضيف android.notification.clickAction=io.openpush.sdk.OPEN_NOTIFICATION كي يفتحها FCM في الخلفية عبر Activity نفسها. يلزم تحديث SDK وإعادة تشغيل خادم اللوحة وإرسال إشعار جديد؛ الإشعارات القديمة لا تتغير بأثر رجعي. لا ترسل clickAction الجديد لتطبيقات لم تُحدث SDK أو لم تضف Activity عبر manifest merging. Firebase Console التي ترسل النقرة إلى launcher تظل تستخدم مسار onCreate/onNewIntent القديم، وقد تظهر واجهة التطبيق قبل التوجيه؛ بيانات Custom Data وحدها لا تغير clickAction الأصلي في FCM.

## الهاتف الحالي: مقارنة خارج SDK

بتاريخ 2026-10-18، حلّ Android كلا طلبي HTTPS وmarket://details?id=com.chikh.nano المحددين بحزمة com.android.vending إلى com.daemon.shelper/.intercept.ost.MarketDeepLinkHandlerActivity. إطلاق market من adb خارج SDK عاد باسم com.heytap.market/.oaps.compatibility.WebBridgeCompatibleActivity. لذلك لم يُعتمد market كإصلاح، ولم يُعطل اعتراض الجهاز أو إعداداته الأمنية.

Router يحافظ على رفض معالج معروف خارج حزمة المتجر الرسمية، ثم يستخدم رابط HTTPS في المتصفح كاحتياط. نجاح startActivity وحده لا يثبت ظهور صفحة Google Play. فتح Google Play الرسمي على هذا الهاتف لم يُثبت بعد. المطلوب جهاز مهيأ بمتجر Google Play دون اعتراض OEM لإثبات هذه الحالة بالكامل.

مرجع آلية Google الرسمية: https://developer.android.com/distribute/marketing-tools/linking-to-google-play

## نتيجة التحقق لهذا التعديل

- بناء الخادم والويب نجح، ونجحت 32 اختبارات خادم و4 اختبارات عميل API.
- نجحت 3 اختبارات Robolectric جديدة: مسار الويب يطلق المتصفح دون MainActivity، والمتجر يستهدف com.android.vending دون browser selector، ووجهة app تحافظ على المضيف بينما PendingIntent الويب يستهدف Activity بلا واجهة.
- بعد تحديث اختبار تكامل كان يفترض دائمًا MainActivity، نجحت اختبارات sample الإحدى عشرة. الإخفاق الأول لذلك الاختبار لم يُعتبر نجاحًا، وتمت إعادة تشغيله بعد التصحيح.
- APK الجديد ثُبت على الهاتف.
- فحص الهاتف: Google Play مثبت ومفعّل، ومع ذلك يعترض النظام طلبي HTTPS وmarket الموجّهين إلى المتجر الرسمي؛ فتح صفحة Google Play الرسمية غير مثبت.
- اختبار UIAutomator لنقرة إشعار الويب لم ينجح: أكد NotificationManager وجود إشعار QA قبل النقر (1) وإذن الإشعارات مفعّل، لكن أداة الواجهة لم تجد عنوانه في درج الإشعارات. لا يعني ذلك أن الإشعار لم يصل/اختفى، ولا يثبت نجاح النقر. طُلب تحقق يدوي منفصل. Deep Link وغياب الرابط لم يُختبرا على الهاتف بهذا الإصدار بعد.
- النقرات من Firebase Console أو إشعارات قديمة قد تبقى موجهة إلى MainActivity؛ المسار بلا واجهة لرسائل Notification + Data يحتاج clickAction الجديد وخادمًا أعيد تشغيله وتطبيقًا محدثًا. دورة FCM كاملة لهذا التعديل لم تُختبر بعد.

### التحقق اليدوي للويب

أكد المستخدم بعد تثبيت النسخة الجديدة والنقر على «OpenPush route QA web» أن المتصفح/اختيار المتصفح فتح مباشرة دون ظهور واجهة OpenPush. هذه نتيجة نقرة يدوية على إشعار SDK محلي، وليست إثبات وصول Notification + Data من FCM عبر clickAction الجديد.

نُشرت كذلك ثلاثة إشعارات مقارنة محلية بعناوين OpenPush direct Play / Article42 / App. نجاح اختبار النشر وحده لا يثبت الوجهة؛ نتائج نقراتها اليدوية تُسجل منفصلة.
