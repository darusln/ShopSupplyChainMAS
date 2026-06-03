import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import java.util.Vector;
import java.util.Enumeration;

public class ShopAgent extends Agent {

    // def parameters
    private int currentStock = 20;
    private int threshold = 10;
    private int targetQuantity = 50;
    private double budget = 10000.00;
    private int maxDeliveryDays = 10;
    private double priceWeight = 0.7;
    private double deliveryWeight = 0.3;
    private String productName = "Item";

    private AID[] supplierAgents;
    private boolean isNegotiating = false;

    private int orderCount = 0;
    private double totalSpent = 0.0;
    private double lastAcceptedPrice = 0.0;
    private int lastAcceptedQuantity = 0;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                if (args.length > 0) productName = (String) args[0];
                if (args.length > 1) currentStock = Integer.parseInt((String) args[1]);
                if (args.length > 2) threshold = Integer.parseInt((String) args[2]);
                if (args.length > 3) targetQuantity = Integer.parseInt((String) args[3]);
                if (args.length > 4) budget = Double.parseDouble((String) args[4]);
                if (args.length > 5) maxDeliveryDays = Integer.parseInt((String) args[5]);
                if (args.length > 6) priceWeight = Double.parseDouble((String) args[6]);
                if (args.length > 7) deliveryWeight = Double.parseDouble((String) args[7]);

                System.out.println("Shop agent " + getAID().getLocalName() + " initialized with custom param");
            } catch (Exception e) {
                System.err.println("Error parsing arguments for " + getAID().getLocalName());
            }
        } else {
            System.out.println("Shop agent " + getAID().getLocalName() + " initialized with default param");
        }

        System.out.println("Configuration");
        System.out.println("Product tracked: " + productName);
        System.out.println("Initial stock: " + currentStock);
        System.out.println("Budget: " + budget);

        // robust DF polling instead of Thread.sleep
        addBehaviour(new TickerBehaviour(this, 3000) {
            private boolean salesStarted = false;
            @Override
            protected void onTick() {
                discoverSuppliers();
                if (!salesStarted && supplierAgents != null && supplierAgents.length > 0) {
                    System.out.println(getLocalName() + "found " + supplierAgents.length + " suppliers");
                    addBehaviour(new SalesSimulator(myAgent, 5000));
                    salesStarted = true;
                }
            }
        });
    }

    private void discoverSuppliers() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("supplying");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            supplierAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                supplierAgents[i] = result[i].getName();
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private class SalesSimulator extends TickerBehaviour {
        public SalesSimulator(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // stop if budget is exhausted
            if (budget <= 0) {
                System.out.println("\nno budget left");
                stop();
                return;
            }

            if (currentStock > 0) {
                int sales = (int) (Math.random() * 5) + 1;
                currentStock = Math.max(0, currentStock - sales);
                System.out.println("\n " + getAID().getLocalName() + " SALES");
                System.out.println("Items sold: " + sales + " | Remaining stock: " + currentStock);
            }

            if (currentStock <= threshold && !isNegotiating) {
                initiateProcurement();
            }
        }
    }

    private void initiateProcurement() {
        if (supplierAgents == null || supplierAgents.length == 0) {
            System.out.println(getAID().getLocalName() + ": No suppliers available for restocking");
            return;
        }

        isNegotiating = true;
        System.out.println(getAID().getLocalName() + ": Stock below threshold (" + threshold + ")");

        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        for (AID agent : supplierAgents) {
            cfp.addReceiver(agent);
        }
        cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
        cfp.setContent(productName + "," + targetQuantity);
        cfp.setReplyByDate(new java.util.Date(System.currentTimeMillis() + 10000));

        addBehaviour(new ContractNetInitiator(this, cfp) {
            @Override
            protected void handleAllResponses(Vector responses, Vector acceptances) {
                ACLMessage bestOffer = null;
                double bestScore = Double.MAX_VALUE;

                Enumeration e = responses.elements();
                while (e.hasMoreElements()) {
                    ACLMessage response = (ACLMessage) e.nextElement();
                    if (response.getPerformative() == ACLMessage.PROPOSE) {
                        try {
                            Proposal p = (Proposal) response.getContentObject();
                            if (p.getDeliveryTime() <= maxDeliveryDays && (p.getPrice() * p.getQuantity()) <= budget) {
                                double score = (p.getPrice() * priceWeight) + (p.getDeliveryTime() * 20.0 * deliveryWeight);
                                if (score < bestScore) {
                                    bestScore = score;
                                    bestOffer = response;
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                StringBuilder rejectionRecord = new StringBuilder("\nrejected offers\n");
                int rejectedCount = 0;

                e = responses.elements();
                while (e.hasMoreElements()) {
                    ACLMessage response = (ACLMessage) e.nextElement();
                    ACLMessage reply = response.createReply();
                    if (response == bestOffer) {
                        reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        reply.setContent("Order confirmed.");

                        try {
                            // track trans to update budget later
                            Proposal winningP = (Proposal) bestOffer.getContentObject();
                            lastAcceptedPrice = winningP.getPrice();
                            lastAcceptedQuantity = winningP.getQuantity();
                        } catch (Exception ex) { }

                    } else {
                        reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        reply.setContent("Better offer found.");

                        try {
                            Proposal rejectedP = (Proposal) response.getContentObject();
                            rejectionRecord.append("- ").append(response.getSender().getLocalName())
                                    .append(" offered ").append(String.format("%.2f", rejectedP.getPrice()))
                                    .append(" (Delivery: ").append(rejectedP.getDeliveryTime()).append(" days)\n");
                            rejectedCount++;
                        } catch (Exception ex) { }
                    }
                    acceptances.addElement(reply);
                }

                // summary of rejected offers
                if (rejectedCount > 0) {
                    System.out.println(rejectionRecord.toString());
                }

                if (bestOffer != null) {
                    try {
                        Proposal winningP = (Proposal) bestOffer.getContentObject();
                        System.out.println("\naccepted offer");
                        System.out.println("Supplier: " + bestOffer.getSender().getLocalName());
                        System.out.println("offered : " + String.format("%.2f", winningP.getPrice()));
                        System.out.println("Delivery: " + winningP.getDeliveryTime() + " days");
                    } catch (Exception ex) {}
                }

                if (bestOffer == null) {
                    System.out.println("\ninsufficient funds");
                    System.out.println(getAID().getLocalName() + ":no affordable offers");
                    System.out.println("Final budget: " + String.format("%.2f", budget));

                    isNegotiating = false;
                    myAgent.doDelete();
                }
            }

            @Override
            protected void handleInform(ACLMessage inform) {
                currentStock += targetQuantity;
                orderCount++;

                // update Budget
                double orderCost = lastAcceptedPrice * lastAcceptedQuantity;
                budget -= orderCost;
                totalSpent += orderCost;

                System.out.println("\n orders");
                System.out.println("Order no: " + orderCount);
                System.out.println("Product: " + productName);
                System.out.println("Supplier: " + inform.getSender().getLocalName());
                System.out.println("Quantity: " + lastAcceptedQuantity);
                System.out.println("Unit price: " + String.format("%.2f", lastAcceptedPrice));
                System.out.println("Total cost: " + String.format("%.2f", orderCost));
                System.out.println("New stock: " + currentStock);
                System.out.println("Total spent: " + String.format("%.2f", totalSpent));
                System.out.println("Budget lef: " + String.format("%.2f", Math.max(0, budget)));

                isNegotiating = false;
            }

            @Override
            protected void handleFailure(ACLMessage failure) {
                System.out.println(getAID().getLocalName() + ": Procurement failed: " + failure.getContent());
                isNegotiating = false;
            }

            protected void handleAllResults(Vector results) {
                isNegotiating = false;
            }
        });
    }

    @Override
    protected void takeDown() {
        System.out.println("Shop agent " + getAID().getName() + " terminating.");
    }
}
