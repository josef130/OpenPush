# إصلاح التوجيه الحالي إلى الويب وGoogle Play

## المسار والاختلافات

يستقبل SDK RemoteMessage عبر خدمة المضيف الحالية؛ لا تُسجل خدمة FCM ثانية. تقرأ
showNotification الغلاف op_v/op_id/op_type/op_target. في الخلفية يعرض FCM Notification+Data
ويضع البيانات في Intent المشغّل عند النقر. يمرر MainActivity Intent إلى handleIntent في
onCreate وonNewIntent. يقرأ المحرك قيم op_* النصية فقط، ويتحقق من الوجهة وقواعد المضيف،
ويحجز op_id في SharedPreferences قبل تنفيذ المعالج.

أضيف Destination في النواة، ويستعمله Route.parse للنوع url في مساري العرض والنقر:

- https:// وhttp://: روابط ويب صالحة، دون userinfo أو منافذ غير افتراضية. يجب أن يوافق المضيف
  على النطاق؛ httpHost مستقل عن httpsHost للحفاظ على سياسة HTTPS الموجودة.
- رابط Play إلى /store/apps/details: استخراج id باستخدام URI وquery decoding، ورفض id
  المكرر أو الفارغ أو غير الصالح، والمسار المشفر وfragment. ينتج رابط HTTPS موحد بمعرف
  التطبيق وحده؛ لا تُمرر معاملات التتبع الأخرى في هذا الإصلاح.
- اسم الحزمة: مقاطع ASCII مفصولة بنقطة، تبدأ بحرف وتحتوي حروفًا/أرقامًا/underscore،
  على الأقل مقطعان وبحد 255 حرفًا، مع رفض كلمات Java المحجوزة. لا تُقبل المسافات أو slash
  أو المقاطع الفارغة. هذا تحقق صيغة فقط، ولا يثبت وجود الحزمة على Google Play.

مثال إشعار صالح، مع معرّف جديد لكل عملية فتح مقصودة:

```text
op_v=1
op_id=play-routing-fix-001
op_type=url
op_target=com.chikh.nano
```

يُحوّل إلى https://play.google.com/store/apps/details?id=com.chikh.nano.
يمكن إرسال الرابط الكامل بدل اسم الحزمة؛ الناتج نفسه. الأنواع screen/deeplink/custom
وواجهة التهيئة القديمة محفوظة. لم تُغيّر إصدارات Firebase أو Gradle للمضيف.

## التهيئة والإطلاق

```java
OpenPush.builder(this, MainActivity.class, R.drawable.ic_notification)
    .httpsHost("example.com")
    .httpHost("example.com") // اختياري إن كان HTTP مطلوبًا لهذا النطاق.
    .httpsHost("play.google.com")
    .preferGooglePlay()
    .launchDiagnostics(report -> {
        // في debug فقط: metadata بلا URL أو query أو payload أو exception message.
        Log.d("OpenPushLaunch", report.action + " " + report.scheme + " "
            + report.requestedPackage + " " + report.resolvedComponent + " " + report.outcome);
    })
    .build();
```

preferGooglePlay محفوظ واختياري؛ ينطبق على وجهات صفحات التطبيقات المصنفة فقط، ولا يوافق
على نطاق من تلقاء نفسه. عند تفعيله يُستعمل ACTION_VIEW مع BROWSABLE وحزمة
com.android.vending. عند ActivityNotFoundException أو SecurityException أو RuntimeException
من طلب الإطلاق يعود SDK إلى رابط HTTPS الموافق عليه، دون حزمة مفروضة. إذا فشل البديل
أيضًا يخرج HANDLER_FAILED دون انهيار؛ يبقى op_id محجوزًا لمنع تكرار محاولة فتحه.

تشخيص الإطلاق يسجل metadata آمنة: action/scheme/requestedPackage/resolvedComponent/outcome.
قد تكون نتيجة resolveActivity غير متاحة بسبب package visibility. يضيف Manifest استعلامات
محدودة لحزمة Play ومعالجات VIEW للويب، دون QUERY_ALL_PACKAGES أو أذونات استخدام التطبيقات.
RETURNED وOPENED لا يثبتان ظهور واجهة المتجر؛ المحرك لا يراقب شاشات التطبيقات الأخرى.

الضمان at-most-once لمحاولة التوجيه داخل سجل آخر 128 معرفًا، مع بقاء السجل بعد موت العملية.
ليس ضمانًا exactly-once لإكمال التنقل؛ الانهيار بعد الحجز قد يفقد التنقل. مسح البيانات أو
إعادة التثبيت/خروج المعرف من السجل يزيل هذا الضمان، ولا يوجد ضمان متعدد العمليات.

## فحص الجهاز

المقارنة السابقة على RMX3852 / API 34 أظهرت أن طلب ACTION_VIEW نفسه، خارج SDK وحتى مع
setPackage("com.android.vending")، يطلق com.daemon.shelper/.intercept.ost.MarketDeepLinkHandlerActivity.
الدليل السابق محفوظ في verification/device-play-explicit-handler.txt والتقرير التاريخي.
هذا دليل على مسار الجهاز/إعداداته في تلك المحاولة، ولا يحدد من ثبّت المعالج أو سبب اعتراضه.
لم تُعطل الحزمة أو الحماية ولم يُحاول تجاوزها.

عند بدء إعادة الاختبار الحالية لم يعرض adb أي جهاز؛ أعاد المستخدم توصيل الهاتف ثم ثُبتت
النسخة المصححة عليه. المقارنة الحالية خارج SDK أعادت resolver الوسيط نفسه، لكن Activity
الإطلاق الفعلية كانت com.heytap.market/.oaps.compatibility.WebBridgeCompatibleActivity رغم
طلب com.android.vending. الدليلان device-play-resolver-routing-fix.txt وdevice-play-outside-sdk-current.txt.

وصل إشعار Console باسم الحزمة بعد التحقق من غياب العملية بـam kill، مع stopped=false.
الدليل routing-fix-package-device.json يحتوي عنوان الإشعار والصورة وBEFORE_DISPATCH؛
محاولة الأداة توقفت عند فحص ظهور التطبيق قبل تنفيذ PendingIntent، ولذلك نتيجتها فشل
وليس نجاحًا للمتجر. ملف النتيجة routing-fix-package-device.txt.

أُرسل بعد ذلك إشعاران يدويان مستقلان دون instrumentation بين الإرسال والنقر:
OpenPush Fix Manual URL وOpenPush Fix Manual Package. السجل الآمن في
verification/routing-fix-launch-metadata.txt رصد طلبَي ACTION_VIEW / https إلى حزمة
com.android.vending؛ في الطلبين اختار النظام com.daemon.shelper وعادت startActivity دون استثناء.
هذه أدلة تنفيذ مسار التوجيه وليست إثباتًا لظهور Google Play.

أبلغ المستخدم أن أحد الإشعارين يعود إلى التطبيق والآخر يصاحبه تعطل؛ لم يحدد أي عنوان
لكل نتيجة، لذلك لا نفترض التعيين. سجل الأعطال المتاح وضع التعطل في com.heytap.market
بخطأ NullPointerException عند MarketType.equalsProtocol، ولم يتضمن تعطل Java لحزمة
io.openpush.sample أو com.android.vending. حُفظت أسماء العمليات وإطارات stack فقط، دون
رسائل الاستثناء أو بيانات التطبيقات الأخرى، في verification/routing-fix-crash-summary.txt.

لا يستطيع catch داخل SDK التقاط انهيار عملية خارجية يحدث بعد عودة startActivity.
ولا نعتبر إعادة توجيه النظام إلى وسيط سببًا لتجاوز إعدادات الحماية أو إجبار component
خاص. **فتح Google Play لم يثبت نجاحه على هذا الجهاز، ومعيار القبول الفيزيائي ما زال غير محقق.**

نجح على الجهاز فتح http://example.com/ وhttps://example.com/ عبر محرك SDK إلى Opera؛
هذه Intents اختبار عبر Activity وليست رسائل FCM. رُصد ACTION_VIEW لكل scheme ومعالج
Opera، ثم BrowserActivity في المقدمة. الأدلة routing-fix-device-web.json وrouting-fix-launch-metadata-final.txt.
لا يعني ذلك اختبار تحميل محتوى الموقع أو بيانات Web إضافية من Firebase.

منع التكرار: المحاولة الأولى لم تثبت انتهاء العملية العادي سريعًا، لذا احتُفظ بها فاشلة
في routing-fix-device-replay.json رغم زيادة DUPLICATE. أُعيد الاختبار بإنهاء المثال فقط
بـForce stop ثم فتحه صراحةً: ثبت غياب العملية، وبقي OPENED=19 وزاد DUPLICATE من 7 إلى 9
عند إعادة معرفَي FCM المحفوظين، في بداية باردة ثم دافئة. النتيجة PASS في
routing-fix-device-replay-force-stop.json. هذا اختبار استمرار السجل بعد الإغلاق، وليس
وصول FCM أثناء Force stop. أُعيد فتح المثال في النهاية لاستعادة الاستقبال الطبيعي.

اختبار غياب Play ناجح آليًا باستخدام Activity ترمي ActivityNotFoundException؛ وكذلك
SecurityException، وفشل البديل. لم يُختبر جهاز فعلي منزوع Play، ولم تُحذف تطبيقات الهاتف.
الانهيار بين حجز op_id والتنقل نفسه ما زال غير مختبر على جهاز.

## الملفات البرمجية المتغيرة

- core/src/main/java/io/openpush/core/Destination.java: التصنيف والتحقق والتطبيع.
- core/src/main/java/io/openpush/core/Route.java: استخدام المصنف مع حفظ بقية البروتوكول.
- sdk/src/main/java/io/openpush/sdk/Router.java: HTTP منفصل، إطلاق Play والبديل والتشخيص.
- sdk/src/main/java/io/openpush/sdk/OpenPush.java: إتاحة HTTP والتشخيص عبر Builder الحالي.
- sdk/src/main/AndroidManifest.xml: استعلامات package visibility محدودة.
- sample/src/main/java/io/openpush/sample/SampleApplication.java: استعمال التهيئة والتشخيص.
- core/src/test/java/io/openpush/core/RouteTest.java: حالات التصنيف والتحقق.
- sdk/src/test/java/io/openpush/sdk/OpenPushTest.java: الإطلاق والبديل والتكرار والتشخيص.
- sample/src/test/java/io/openpush/sample/PracticalIntegrationTest.java: الحزمة والرابط يصلان لنفس Intent.
- sample/src/androidTest/java/io/openpush/sample/DeviceAcceptanceTest.java: مجسات الجهاز وتجنب cache قديم.
- scripts/test-device.ps1: تشغيل ثمانية اختبارات محلية على الجهاز.
- README.md وdocs/TESTING.md وdocs/PLAY-STORE-TEST.ar.md وهذا التقرير: توثيق السلوك والنتائج.

## نتائج الاختبارات الآلية المنفذة

- النواة: PASS — 76 فحصًا.
- SDK: PASS — 20 حالة Robolectric.
- المثال: PASS — 9 حالات Robolectric.
- الجهاز: PASS — 8 عقود مختارة، وليست إثبات ظهور Google Play.
- البناء: PASS — Release AAR وAPK Firebase وحزمة AndroidTest.
- Lint: صفر أخطاء، خمسة تحذيرات تحديث تبعيات؛ لم تُحدّث مكتبات المضيف.

تفصيل كل حالة JVM من ملفات JUnit XML الفعلية:

- PASS — io.openpush.sdk.OpenPushTest.malformedStoreIdsAndRandomStringsNeverLaunchOrClaim
- PASS — io.openpush.sdk.OpenPushTest.httpApprovalIsIndependentFromHttpsAndPreservesWebDestination
- PASS — io.openpush.sdk.OpenPushTest.playPreferenceIsOptIn
- PASS — io.openpush.sdk.OpenPushTest.differentNotificationsKeepIndependentClickPayloads
- PASS — io.openpush.sdk.OpenPushTest.missingNativeStoreFallsBackToSameApprovedHttpsOnceAndContainsFailure
- PASS — io.openpush.sdk.OpenPushTest.routesOnlyOnClickAndDeduplicatesAcrossAdapters
- PASS — io.openpush.sdk.OpenPushTest.localNotificationDoesNotNavigateUntilTap
- PASS — io.openpush.sdk.OpenPushTest.opensOnlyAllowlistedHttpsAndPreservesArguments
- PASS — io.openpush.sdk.OpenPushTest.disabledChannelDoesNotDisplayOrNavigate
- PASS — io.openpush.sdk.OpenPushTest.missingBrowserIsContainedAndNotRetried
- PASS — io.openpush.sdk.OpenPushTest.customActionReceivesImmutableArgumentsAndFailsSafely
- PASS — io.openpush.sdk.OpenPushTest.rejectsUnknownActionAndInvalidPayloadWithoutClaimingId
- PASS — io.openpush.sdk.OpenPushTest.notificationPermissionDenialAndGrantAreRespected
- PASS — io.openpush.sdk.OpenPushTest.deniedNativeLaunchFallsBackAndReportsOnlySafeMetadata
- PASS — io.openpush.sdk.OpenPushTest.deepLinkRejectsUnregisteredPathsAndTraversal
- PASS — io.openpush.sdk.OpenPushTest.channelNameIsNotOverwrittenByDelivery
- PASS — io.openpush.sdk.OpenPushTest.nativePlayPreferenceStillRequiresHostApprovalAndKeepsOtherUrlsGeneric
- PASS — io.openpush.sdk.OpenPushTest.barePackageNormalizesAndNativeLaunchIsClaimedOnlyOnce
- PASS — io.openpush.sdk.OpenPushTest.diagnosticCallbackCannotBreakRouting
- PASS — io.openpush.sdk.OpenPushTest.deepLinkMustHaveAnAllowedDeclaredDestination
- PASS — io.openpush.sample.PracticalIntegrationTest.notificationPlusDataOpensHttpsOnColdStartOnlyOnce
- PASS — io.openpush.sample.PracticalIntegrationTest.playHttpsAndBarePackageOpenTheSameNativeStoreDestination
- PASS — io.openpush.sample.PracticalIntegrationTest.declaredDeepLinkOpensActualArticleViewWithContentAndLevel
- PASS — io.openpush.sample.PracticalIntegrationTest.multipleOutstandingNotificationsKeepChangedUrlsAndWarmEntryCorrect
- PASS — io.openpush.sample.PracticalIntegrationTest.localSimulationNeverIncrementsFcmCallbackCounter
- PASS — io.openpush.sample.PracticalIntegrationTest.invalidLinksAndUnsupportedDestinationsFailWithoutNavigation
- PASS — io.openpush.sample.PracticalIntegrationTest.existingServiceCallbacksAndUnrelatedDataArePreserved
- PASS — io.openpush.sample.PracticalIntegrationTest.ordinaryNotificationUsesExistingHostHandlerAndOpensHome
- PASS — io.openpush.sample.PracticalIntegrationTest.dataOnlyAndCombinedMessagesRemainDistinctFixtures

حالات الجهاز الثمانية في التشغيل الناجح:

- PASS — io.openpush.sdk.ClickTest.repeatedEntryPointsAndNewAdapterDoNotDispatchAgain
- PASS — io.openpush.sdk.ClickTest.rejectedRouteDoesNotConsumeId
- PASS — io.openpush.sdk.ClickTest.malformedAndUnrelatedExtrasNeverDispatch
- PASS — io.openpush.sdk.ClickTest.thrownCallbackRemainsClaimed
- PASS — io.openpush.sdk.ClickTest.boundedLedgerEvictsOldest
- PASS — io.openpush.sample.DeviceAcceptanceTest.localHostAndOutstandingNotifications
- PASS — io.openpush.sample.DeviceAcceptanceTest.localRejectedIntentsAndReplay
- PASS — io.openpush.sample.DeviceAcceptanceTest.localDestinationClassificationContracts

تقرير JSON لكل النتائج: verification/routing-fix-tests.json.
APK Firebase المختبر: build/practical-apks/OpenPush-routing-fix-debug.apk.
SHA-256: bb17d87696317f02320897d6f1fd17f7e42067d4b4e83a4a283077f633aebbfe.
Release AAR: sdk/build/outputs/aar/sdk-release.aar؛ يحتاج core وFirebase للمضيف كما في README.
