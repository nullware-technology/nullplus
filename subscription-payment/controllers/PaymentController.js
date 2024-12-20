const StripeService = require('../services/StripeService');
const SubscriptionService = require('../services/SubscriptionService');

const express = require('express');
const app = express();

app.post('/payment/pay', async (req, res, next) => {
  try {
    var paymentData = req.body;

    var url = await StripeService.createPaymentSession(paymentData);

    res.send(url);
  } catch (error) {
    next(error);
  }
});

app.get('/payment/success', (req, res) => {
  res.send('Pagamento realizado com sucesso!');
});

app.get('/payment/cancel', (req, res) => {
  // Lógica caso queira enviar email depois.

  res.json({ cancelUrl: 'https://nullplus.com' });
});

app.get('/payment/update/:idStripeUser', async (req, res, next) => {
  try {
    // Recuperar ID da sessão.

    // Recuperar idStripe do usuário do outro serviço.
    var idStripeUser = req.params.idStripeUser;

    const url = await StripeService.editPaymentMethod(idStripeUser);

    res.status(200).send(url);
  } catch (error) {
    next(error);
  }
});

app.post('/payment/webhook', async (request, response) => {
  const event = request.body;

  switch (event.type) {
    case 'checkout.session.completed':
      const eventData = event.data.object;

      const subscription = await StripeService.retrieveSubscriptionFromSession(eventData.id);
      SubscriptionService.createSubscription(subscription);

      // Enviar email dando boas vindas, etc.

      break;
    case 'customer.subscription.deleted':
      const canceledSubscription = event.data.object;

      SubscriptionService.cancelSubscription(canceledSubscription.id);

      break;
    case 'customer.subscription.updated':
      const updatedSubscription = event.data.object;
      const previousSubscription = event.data.previous_attributes;

      if (previousSubscription?.plan?.id && updatedSubscription.plan?.id) {
        if (updatedSubscription.plan.id !== previousSubscription.plan.id) {
          const newPlanStripeId = updatedSubscription.plan.id;
          SubscriptionService.updatePlan(updatedSubscription.id, newPlanStripeId);
        }
      }
      
      break;
    case 'payment_method.attached':
      const newPaymentMethod = event.data.object;
      const customerId = newPaymentMethod.customer;

      StripeService.removeOldPaymentMethod(newPaymentMethod, customerId);

      break;
    default:
    // console.log(`Unhandled event type ${event.type}`);
  }

  response.json({ received: true });
});

module.exports = app;