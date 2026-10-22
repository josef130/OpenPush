# اختبار FCM المقارن: المقدمة والخلفية وإزالة المهمة

التاريخ: 2026-10-18. الهاتف المتصل: RMX3852، Android API 34، اتصال USB مصرح به كما أظهر adb devices -l.

## النتائج المثبتة في هذه الجولة

1. **المقدمة — OpenPush Lifecycle Foreground 07**، معرف 4cc99910-f535-4ecc-9966-197d9a10ef9d. التطبيق مفتوح وغير موقوف. سجل OpenPush استقبال WEB، وإنشاء DIRECT_ACTIVITY PendingIntent، ثم DISPLAYED. وجدنا معرف الرسالة نفسه في سجل منع تكرار النقرات. توجد رسالة Play أخرى متداخلة في السجل، لذلك لا ننسب جميع فروق العدادات إلى 07 وحدها. التحقق بمعرف الرسالة يثبت نقرتها.
2. **الخلفية — OpenPush Lifecycle Background 08**، معرف a0322197-6f4d-4a55-87db-711f24f61b19. بعد HOME كان stopped=false. ظهر في cmd notification list المفتاح 0|io.openpush.sample|0|a0322197-6f4d-4a55-87db-711f24f61b19|10527 المطابق لمعرف تقرير المنصة. عداد FCM_CALLBACK لم يرتفع: هذا متوقع لرسالة Notification+Data في الخلفية التي يعرضها Firebase، وليس فشل استقبال.
3. **الإزالة من التطبيقات الأخيرة — OpenPush Lifecycle Removed 09**، معرف d10a2a96-6fc1-4c9b-ae8c-32b6442ae7fe. حددنا بطاقة OpenPush باسمها ومعرف task_header في واجهة launcher وسحبناها وحدها. مباشرة بعد السحب أصبحت stopped=true، واختفت العملية، وسجل Android USER REQUESTED / REMOVE TASK للعملية 9103. لم نستخدم force-stop ولا أغلقنا جميع التطبيقات. أرسلنا 09 في هذه الحالة؛ المنصة أظهرت قبول إرسال واحد وفشل صفر، ولم يظهر إشعار OpenPush ولم يرتفع FCM_CALLBACK عند الفحص التالي. مدة المراقبة محدودة، ولا ندعي ضمان عدم التسليم إلى الأبد.

## أين يتوقف المسار؟

قبول FCM يعمل في الحالات الثلاث. عرض Android يعمل في المقدمة والخلفية العادية. الفرق المثبت بعد إزالة المهمة هو حالة الحزمة stopped=true قبل وصول الرسالة. لذلك العائق المشاهد يسبق استدعاء خدمة التطبيق وإنشاء الإشعار. لا يوجد دليل من هذه التجربة على أن كود التقارير منع العرض، ولا نحتاج إعادة كتابة SDK أو تغيير بروتوكول النقر.

## فحص الكود والتهيئة

- خدمة التطبيق io.openpush.sample.MessagingService تعالج onMessageReceived وتستدعي HostMessageHandler ثم OpenPush.showNotification.
- المانيفست المدمج يحتوي خدمة المضيف بأولوية افتراضية وخدمة Firebase الافتراضية بأولوية -500؛ وجود خدمة Firebase الاحتياطية لا يعني وجود مستهلكين مستقلين متنافسين بأولوية واحدة. خدمة المضيف هي المقصودة.
- POST_NOTIFICATIONS ممنوحة. createChannel وareNotificationsEnabled والتحقق من أهمية القناة موجودة، وقد ثبت النشر في التجربتين الأوليين.
- قيود appops الخاصة بـ RUN_ANY_IN_BACKGROUND وRUN_IN_BACKGROUND أظهرت الوضع الافتراضي allow. هذا لا ينفي سياسة مصنّع الجهاز عند إزالة المهمة، ولا يثبت أن استثناء البطارية سيعالجها.
- أحداث التقارير في ReceiptQueue.enqueue محاطة بمعالجة أخطاء وتعمل خارج إرسال طلب شبكي مباشر في callback. لا يوجد force-stop أو stopWithTask أو killProcess في مسار الاستقبال.
- بقيت op_v/op_id/op_type/op_target ومنع التكرار وتوجيه الويب وGoogle Play دون تعديل في هذه الجولة.

## الخطوة التالية والعائق

فحص إعدادات البطارية/التشغيل في الخلفية من واجهة إعدادات OpenPush يتطلب توفر الهاتف دون تدخل متزامن. انتقلت الواجهة إلى تطبيقات أخرى أثناء المحاولات، لذلك لم نغير إعدادًا عشوائيًا أو ننسبه إلى OpenPush. طلبنا فترة قصيرة دون استخدام لاستكمال ذلك. لم يتم اعتماد إصلاح إعدادات أو إعادة بناء/تثبيت جديدة في هذه الجولة لأن تغييرًا علاجيًا لم يثبت بعد. لا ندعي اكتمال الإصلاح.

الأدلة: verification/lifecycle-live-log.txt، verification/lifecycle-comparison-results.json. السجلات مقيدة بوسوم OpenPush؛ لا تتضمن رموز FCM أو محتوى الرسائل أو الوجهات كاملة.

مرجع سلوك Notification+Data: https://firebase.google.com/docs/cloud-messaging/android/receive-messages . مرجع حالة الإيقاف: https://firebase.google.com/support/troubleshooter/fcm/delivery/diagnose/android/received/sent/notification/forcestop-true .
