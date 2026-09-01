class PaymentDeclinedException extends Exception {}

class PaymentService {

    // Flagged: doCharge() throws PaymentDeclinedException, swallowed silently.
    void chargeUnsafe() {
        try {
            doCharge();
        } catch (Exception e) {
        }
    }

    // Not flagged: the exception is logged before being swallowed.
    void chargeSafe() {
        try {
            doCharge();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void doCharge() throws PaymentDeclinedException {}
}
