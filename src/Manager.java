import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashMap;
import java.util.HashSet;

public class Manager extends Agent {

    @Override
    protected void setup() {
        this.addBehaviour(new cardRequester(this, 100));
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("manager");
        sd.setName("Manager");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    public class cardRequester extends TickerBehaviour {

        HashMap<AID, Integer> cardAgents = new HashMap<>();
        int countOfCards = 0;
        int step = 0;
        int readyCards = 0;

        public cardRequester(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            switch (step) {
                case 0:
                    countOfCards = 0;
                    readyCards = 0;
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("card");
                    template.addServices(sd);
                    // ищем все билеты
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        countOfCards = result.length;
                    } catch (FIPAException ex) {
                        ex.printStackTrace();
                    }
                    // передача инфы о готовности билета
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    ACLMessage msg = myAgent.receive(mt);
                    if (msg != null) {
                        int complexity = Integer.parseInt(msg.getContent());
                        AID card = msg.getSender();
                        cardAgents.put(card, complexity);
                        //System.out.println(cardAgents.size());
                        if (cardAgents.size() == countOfCards && cardAgents.size() != 0) //если количество всех агентов-билетов с количеством "готовых" агентов-билетов
                        {
                            step = 1;
                            System.err.println("Все билеты собрали вопросы и сообщили Менеджеру об этом");
                        }
                    } else {
                        block();
                    }

                    break;
                case 1:
                    //считаем среднюю сложность билетов
                    int summary = 0;
                    for (int c : cardAgents.values()) {
                        summary += c;
                    }
                    double average = summary / (countOfCards * 1.0);
                    System.out.println("average = " + average);
                    //сообщаем все билетам среднюю сложность и они в поведении Exchanger самоопределяются
                    ACLMessage message = new ACLMessage(ACLMessage.INFORM);
                    for (AID aid : cardAgents.keySet()) {
                        message.addReceiver(aid);
                    }
                    message.setContent("" + average);
                    myAgent.send(message);
                    step = 2;
                    break;
                case 2:
                    // билеты уведомляют что поменяли сервисы
                    mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
                    msg = myAgent.receive(mt);
                    if (msg != null) {
                        if (msg.getContent().equals("Я поменял сервис")) {
                            readyCards++;
                            System.out.println(readyCards);
                        }
                        if (readyCards == countOfCards) {
                            System.err.println("Все поменяли свои сервисы");
                            System.out.println(readyCards);
                            //посылает разрешение на начало обмена
                            message = new ACLMessage(ACLMessage.CONFIRM);
                            for (AID aid : cardAgents.keySet()) {
                                message.addReceiver(aid);
                            }
                            message.setContent("Начинайте обмен!");
                            myAgent.send(message);
                            step = 3;
                        }
                    } else {
                        block();
                    }
                    break;
                    //собираем инициаторов
                case 3:
                    template = new DFAgentDescription();
                    sd = new ServiceDescription();
                    sd.setType("initiator");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        countOfCards = result.length;
                        System.err.println("Инициаторов: "+countOfCards);
                    } catch (FIPAException ex) {
                        ex.printStackTrace();
                    }
                    step = 4;
                case 4:
                    //ожидаем окончание обмена билетов по количеству инициаторов
                    mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM_REF);
                    msg = myAgent.receive(mt);
                    if (msg != null) {
                        countOfCards--;
                        System.err.println(countOfCards);
                        if (countOfCards <= 0) //если все билеты закончили обмен
                        {
                            System.out.println();
                            System.out.println("БИЛЕТЫ ЗАКОНЧИЛИ ОБМЕН И ГОТОВЫ!");
                            System.out.println();

                            step = 5;
                        }
                    } else {
                        block();
                    }
                    break;
                case 5:
                    //собираем всех инициаторов и обычных билетов
                    message = new ACLMessage(ACLMessage.PROPAGATE);
                    HashSet<AID> cards = new HashSet<>();
                    template = new DFAgentDescription();
                    sd = new ServiceDescription();
                    sd.setType("initiator");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        for (DFAgentDescription s : result)
                            cards.add(s.getName());
                    } catch (FIPAException ex) {
                        ex.printStackTrace();
                    }

                    template = new DFAgentDescription();
                    sd = new ServiceDescription();
                    sd.setType("simple");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        for (DFAgentDescription s : result)
                            cards.add(s.getName());
                    } catch (FIPAException ex) {
                        ex.printStackTrace();
                    }
                    for (AID aid : cards) {
                        message.addReceiver(aid);
                    }

                    //отправляем требование показать себя
                    message.setContent("Покажи результат");
                    message.setLanguage("1");
                    myAgent.send(message);
                    step = 6;
                   // myAgent.doDelete();
                    break;

            }
        }
    }
}