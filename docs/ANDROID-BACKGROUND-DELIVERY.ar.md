# وصول الإشعارات بعد إغلاق التطبيق على هاتف realme

## التحقق على RMX3852 — 2026-10-19

كان إذن POST_NOTIFICATIONS ممنوحًا، لكن إزالة بطاقة OpenPush من التطبيقات الأخيرة كانت تحول الحزمة إلى stopped=true، مع سبب الخروج REMOVE TASK. ثبت وصول الإشعارات في المقدمة والخلفية العادية في الاختبارات السابقة.

وصلنا إلى إعداد الهاتف الخاص بتطبيق OpenPush Sample: App info → Battery usage. ظهر Allow foreground activity = On وAllow background activity = Off. فعّلنا Allow background activity لهذا التطبيق فقط، وقبلنا مربع Allow all background activity for OpenPush Sample. تحققنا أن مفتاح الخلفية أصبح On.

بعد ذلك فتحنا التطبيق مرة واحدة، وحددنا بطاقة OpenPush بالاسم ومعرف task_header في التطبيقات الأخيرة، وسحبنا هذه البطاقة وحدها. اختفت العملية وبقيت stopped=false مباشرةً وكذلك عند فحص لاحق. إذن تغير سلوك الإغلاق فعليًا بعد تعديل إعداد الهاتف. لم نستخدم force-stop أو قفل البطاقة أو خدمة مستمرة لإبقاء العملية حية، ولم نغير توجيه الروابط أو بروتوكول FCM.

بعد تسجيل الدخول إلى اللوحة أرسلنا ثلاث رسائل FCM حقيقية. الاختبار 01 غير حاسم لأن عملية التطبيق ظهرت لكن لم نلتقط عنوان الإشعار المحدد؛ لم نعتبره نجاحًا. الاختبار 02 (e64688a7-6a2a-4f28-9f2b-037f16c2fbdd) نجح: قبل الإرسال لا توجد عملية وstopped=false، ثم ظهر عنوان OpenPush Closed Battery Fix 02 في سجل إشعارات Android بالمفتاح openpush:معرف_الرسالة. كررنا إزالة البطاقة للاختبار 03 (acd6f090-27bc-4e5b-88cb-cf5ee021b050)، وتحققنا مباشرة قبل الإرسال من SCREEN_STATE_OFF وDozing، ثم ظهر عنوان OpenPush Closed Screen Off 03 في Android أيضًا. هذه أدلة نشر فعلية وليست مجرد قبول FCM، ولا تثبت قراءة المستخدم أو ظهور Google Play.

الأدلة: verification/background-battery-fix-results.json وverification/closed-battery-fix-02-receipt.json وverification/closed-screen-off-03-receipt.json. مراقب الاختبار 03 سجل الشاشة On عند بدايته، ثم أطفأناها وتحققنا من Off مباشرة قبل الإرسال؛ القراءة الأخيرة محفوظة في verification/closed-screen-off-03-immediate-state.txt. لم نُخفِ القراءة الأولية المختلفة.

## حدود النتيجة

هذا إعداد خاص بالهاتف المختبر، وليس وعدًا بأن كل جهاز يحتاج الإعداد نفسه. لا يستطيع SDK إلغاء force-stop الحقيقي الذي يفرضه المستخدم من إعدادات Android. لا حاجة لتحديث مكتبات Firebase أو إعادة بناء SDK لمعالجة هذا الإعداد. قد تزيد طاقة الخلفية لتطبيق OpenPush بعد السماح بها، كما أوضح مربع النظام.

توصية realme الرسمية للإشعارات المفقودة تشمل السماح بالنشاط الخلفي والتشغيل التلقائي عندما يكون الخيار متاحًا: https://www.realme.com/bd/support/kw/doc/2134413 . الخيار Allow auto launch لم يظهر في صفحة البطارية التي فحصناها؛ لم نغيره أو ندّعِ تفعيله.

مرجع Firebase لحالة الإيقاف: https://firebase.google.com/support/troubleshooter/fcm/delivery/diagnose/android/received/sent/notification/forcestop-true .
