const PaymentService = require('../service/PaymentService');
const SubscriptionService = require('../service/SubscriptionService');

const express = require('express');
const app = express();

app.post('/payment/pay', async (req, res) => {
  var paymentData = req.body;

  var url = await PaymentService.createPaymentSession(paymentData);

  res.send(url);
});

app.get('/payment/success', (req, res) => {
  res.send('Pagamento realizado com sucesso!');
});

app.get('/payment/cancel', (req, res) => {
  // Lógica caso queira enviar email depois.

  res.json({ cancelUrl: 'https://nullplus.com' });
});

app.post('/payment/webhook', async (request, response) => {
  const event = request.body;

  switch (event.type) {
    case 'checkout.session.completed':
      const eventData = event.data.object;

      SubscriptionService.createSubscription(eventData);

      // Enviar email dando boas vindas, etc.

      break;
    case 'payment_method.attached':
      const paymentMethod = event.data.object;

      break;
    default:
      // console.log(`Unhandled event type ${event.type}`);
  }

  // Return a response to acknowledge receipt of the event
  response.json({ received: true });
});

module.exports = app;