import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.ActionExecutor;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExamCardAgent extends Agent {
    private Question firstQuestion;
    private Question secondQuestion;
    public double average = 0;
    boolean isChanged = false;

    HashSet<AID> simpleCards;

    @Override
    protected void setup() {
        System.out.println("Bilet " + getLocalName() + " is ready!");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(ExamCardAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("card");
        sd.setName("MyCard");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new QuestionRequester(this,3000));
    }

    private class QuestionRequester extends TickerBehaviour{
        private HashSet<AID> questionAgents;
        boolean isSendMsgToManager = false;
        public QuestionRequester(Agent a, long period){
            super(a,period);
        }

        @Override
        protected void onTick() {
            if (isSendMsgToManager)
            {
                return;
            }

            if(firstQuestion!=null && secondQuestion!=null && !isSendMsgToManager){
                System.out.println("Билет готов - " + this.myAgent.getLocalName() + ": " + firstQuestion.toString() + " : " + secondQuestion.toString());
                AID manager = null;
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("manager");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result.length != 0)
                    {
                        manager = result[0].getName();
                    }
                    else
                    {
                        return;
                    }
                }
                catch (FIPAException fe)
                {
                    fe.printStackTrace();
                }
                ACLMessage message = new ACLMessage(ACLMessage.INFORM);
                message.addReceiver(manager);
                message.setContent(("" + (firstQuestion.complexity + secondQuestion.complexity)));
                message.setReplyWith(("ready" + System.currentTimeMillis()));
                myAgent.send(message);
                block();
                isSendMsgToManager=true;
                return;
            }
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("question");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                questionAgents = new HashSet<AID>();
                for (int i = 0; i < result.length; ++i)
                {
                    questionAgents.add(result[i].getName());
                }
            }
            catch (FIPAException fe)
            {
                fe.printStackTrace();
            }
            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
            for(AID aid :questionAgents)
                message.addReceiver(aid);

            message.setContent("Дай себя");
            message.setReplyWith("request" + System.currentTimeMillis());
            myAgent.send(message);
            //myAgent.addBehaviour(new QuestionPicker());
            System.out.println(myAgent.getLocalName() + " запросил вопросы");


        }


    }
  /*  private class QuestionPicker extends Behaviour{
        //принимает сообщения от вопросов, которые готовы "отдать" себя, и проверяет, можно ли взять вопрос в билет
    }*/





    @Override
    public void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

}

