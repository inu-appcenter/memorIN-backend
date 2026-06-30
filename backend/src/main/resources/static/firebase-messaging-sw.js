importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js');
firebase.initializeApp({
    apiKey: "AIzaSyBFw_Rqawiefd3PigXK40hJ1_TNzapmdes",
    authDomain: "chat-test-b9025.firebaseapp.com",
    projectId: "chat-test-b9025",
    storageBucket: "chat-test-b9025.firebasestorage.app",
    messagingSenderId: "570575560014",
    appId: "1:570575560014:web:702f84f1116ad66408521f"
});

const messaging = firebase.messaging();

// 탭이 백그라운드일 때 들어온 알림 표시
messaging.onBackgroundMessage((payload) => {
    const n = payload.notification || {};
    self.registration.showNotification(n.title || "새 메시지", { body: n.body || "" });
});