const express = require('express');
const admin = require('firebase-admin');
const axios = require('axios');
const crypto = require('crypto');
require('dotenv').config();

const app = express();
app.use(express.json());

// 1. Cấu hình Firebase Admin
try {
    const serviceAccount = require("./your-firebase-service-account.json");
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
} catch (e) {
    console.error("Firebase Admin Error: Ensure your-firebase-service-account.json is present");
}
const db = admin.firestore();

// 2. Cấu hình MoMo (Lấy từ biến môi trường để bảo mật)
const partnerCode = (process.env.MOMO_PARTNER_CODE || "MOMO").trim();
const accessKey = (process.env.MOMO_ACCESS_KEY || "").trim();
const secretKey = (process.env.MOMO_SECRET_KEY || "").trim();
const momoEndpoint = "https://test-payment.momo.vn/v2/gateway/api/create";

app.post('/api/payments/momo/create', async (req, res) => {
  const { userId, amount } = req.body;
  const ngrokUrl = (process.env.WEBHOOK_URL || "").split('/api/')[0];

  try {
    const amountStr = amount.toString();
    const orderInfo = "Upgrade_Premium";
    const requestId = "REQ" + Date.now();
    const orderId = requestId;
    const ipnUrl = (process.env.WEBHOOK_URL || "").trim();
    const redirectUrl = "vocabmaster://payment-success";
    const requestType = "captureWallet";
    const extraData = Buffer.from(userId).toString('base64');

    const rawSignature = `accessKey=${accessKey}&amount=${amountStr}&extraData=${extraData}&ipnUrl=${ipnUrl}&orderId=${orderId}&orderInfo=${orderInfo}&partnerCode=${partnerCode}&redirectUrl=${redirectUrl}&requestId=${requestId}&requestType=${requestType}`;

    const signature = crypto.createHmac('sha256', secretKey).update(rawSignature).digest('hex');

    const requestBody = {
        partnerCode,
        accessKey,
        requestId,
        amount: Number(amount),
        orderId,
        orderInfo,
        redirectUrl,
        ipnUrl,
        extraData,
        requestType,
        signature,
        lang: "vi",
        autoCapture: true
    };

    const response = await axios.post(momoEndpoint, requestBody);

    if (response.data.resultCode === 0) {
        return res.json(response.data);
    }
    throw new Error("MoMo API Error: " + response.data.message);

  } catch (error) {
    console.log("--- CHẾ ĐỘ GIẢ LẬP THANH TOÁN KÍCH HOẠT ---");
    const mockPayUrl = `${ngrokUrl}/api/mock-payment-page?userId=${userId}&amount=${amount}`;
    res.json({
      payUrl: mockPayUrl,
      resultCode: 0,
      message: "Sử dụng cổng giả lập VocabMaster"
    });
  }
});

// Trang giả lập thanh toán
app.get('/api/mock-payment-page', (req, res) => {
    const { userId, amount } = req.query;
    res.send(`
        <html>
            <head><title>Cổng Thanh Toán Giả Lập</title><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="font-family:sans-serif; text-align:center; padding: 50px; background-color: #fce4ec;">
                <div style="max-width: 400px; margin: auto; background: white; padding: 30px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.1);">
                    <h2 style="color: #A50064;">Thanh Toán Giả Lập MoMo</h2>
                    <p>Số tiền: <span style="font-size: 24px; color: #A50064; font-weight: bold;">\${amount}đ</span></p>
                    <hr/>
                    <div id="timer" style="font-size: 20px; color: #ff5252; margin: 20px 0; font-weight: bold;">Thời gian còn lại: 02:00</div>
                    <button id="btnConfirm" onclick="confirmPayment()" style="background:#A50064; color:white; padding:15px 30px; border:none; border-radius:10px; cursor:pointer; width: 100%; font-size: 16px; font-weight: bold;">XÁC NHẬN THANH TOÁN</button>
                    <p style="margin-top: 20px; color: #666; font-size: 14px;">Giao dịch sẽ tự động hủy sau 2 phút.</p>
                </div>
                <script>
                    let timeLeft = 120;
                    const timerElement = document.getElementById('timer');
                    const countdown = setInterval(() => {
                        if (timeLeft <= 0) {
                            clearInterval(countdown);
                            alert("Thời gian thanh toán đã hết.");
                            window.location.href = "vocabmaster://payment-success?status=expired";
                        } else {
                            let minutes = Math.floor(timeLeft / 60);
                            let seconds = timeLeft % 60;
                            timerElement.innerHTML = "Thời gian còn lại: " + (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
                        }
                        timeLeft -= 1;
                    }, 1000);
                    function confirmPayment() { window.location.href = '/api/mock-success?userId=\${userId}'; }
                </script>
            </body>
        </html>
    `);
});

app.get('/api/mock-success', async (req, res) => {
    const { userId } = req.query;
    try {
        const premiumUntil = new Date();
        premiumUntil.setFullYear(premiumUntil.getFullYear() + 1);
        await db.collection('users').doc(userId).update({
            isPremium: true,
            premiumUntil: admin.firestore.Timestamp.fromDate(premiumUntil)
        });
        res.redirect("vocabmaster://payment-success");
    } catch (e) { res.send("Lỗi: " + e.message); }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Server running on port \${PORT}`));
