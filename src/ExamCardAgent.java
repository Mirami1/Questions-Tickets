import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.ActionExecutor;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExamCardAgent extends Agent {
    private Question firstQuestion;
    private Question secondQuestion;
    public double average = 0;
    boolean isChangedInitiator = false; // для обмена вопросами
    boolean isChanged = false;
    HashSet<AID> simpleCards; // простые вопросы
    Behaviour exchanger = null;
    Behaviour Intiiator = null;
    Behaviour Simple = null;

    @Override
    protected void setup() {
        System.out.println("Bilet " + getLocalName() + " is ready!");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(ExamCardAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        //регестрируемся
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
        // добавляем поведение запроса вопроса с периодичностью в 3 секунды
        addBehaviour(new QuestionRequester(this, 3000));
        //Thread.sleep(5000);
        exchanger = new Exchanger(this, 6000);
        addBehaviour(exchanger);
    }

    private class QuestionRequester extends TickerBehaviour {
        private HashSet<AID> questionAgents; // словарь для хранения вопросов
        boolean isSendMsgToManager = false; // отправлен ли билет менеджеру

        public QuestionRequester(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (isSendMsgToManager) {
                return;
            }

            // если билет заполнен, но ещё не отправлялся менеджеру, находим менеджера по описанию и информируем его
            if (firstQuestion != null && secondQuestion != null && !isSendMsgToManager) {
                System.out.println("Билет готов - " + this.myAgent.getLocalName() + ": " + firstQuestion.toString() + " : " + secondQuestion.toString());
                AID manager = null;
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("manager");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result.length != 0) {
                        manager = result[0].getName();
                    } else {
                        return;
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
                ACLMessage message = new ACLMessage(ACLMessage.INFORM);
                message.addReceiver(manager);
                message.setContent(("" + (firstQuestion.complexity + secondQuestion.complexity)));
                message.setReplyWith(("ready" + System.currentTimeMillis()));
                myAgent.send(message);
                block();
                isSendMsgToManager = true;
                return;
            }
            // иначе ищем ищем все вопросы и забрасываем в словарь, потом делаем всем запрос вопросов
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("question");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                questionAgents = new HashSet<AID>();
                for (int i = 0; i < result.length; ++i) {
                    questionAgents.add(result[i].getName());
                }
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
            for (AID aid : questionAgents)
                message.addReceiver(aid);

            message.setContent("Дай себя");
            message.setReplyWith("request" + System.currentTimeMillis());
            myAgent.send(message);
            // добваляем простое поведение на сбор вопроса
            myAgent.addBehaviour(new QuestionPicker());
            System.out.println(myAgent.getLocalName() + " запросил вопросы");


        }


    }

    private class QuestionPicker extends Behaviour {
        //принимает сообщения от вопросов, которые готовы "отдать" себя,
        // и проверяет, можно ли взять вопрос в билет

        int step = 0;
        ACLMessage msg;
        MessageTemplate mt;

        @Override
        public void action() {
            switch (step) {
                case 0:
                    // принимаем сообщения от вопросов только по шаблону Предложение
                    mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                    msg = myAgent.receive(mt);
                    // в зависимости от того занят ли первый или второй вопрос в билете, принимаем решения
                    // и отвечаем принятием предложения, в противном случаем блокируемся
                    if (msg != null) {
                        if (firstQuestion == null) {
                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                            reply.setContent("Беру");
                            myAgent.send(reply);
                            step = 1;
                            System.out.println("Билет " + myAgent.getLocalName() + " хочет взять первый вопрос  ");
                        } else if (secondQuestion == null) {
                            Question q = new Question(msg.getContent());
                            if (!firstQuestion.theme.equals(q.theme)) {
                                ACLMessage reply = msg.createReply();
                                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                reply.setContent("Беру");
                                myAgent.send(reply);
                                step = 2;
                                System.out.println("Билет " + myAgent.getLocalName() + " хочет взять второй вопрос " + " - " + q.toString());
                            }
                        }
                    } else {
                        block();
                    }
                    break;
                case 1:
                    // принимаем сообщения от вопросов только по шаблону Согласие или Отказ
                    mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.AGREE), MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
                    msg = myAgent.receive(mt);
                    if (msg != null) {
                        // заполняем поле первого вопроса
                        if (msg.getPerformative() == ACLMessage.AGREE) {
                            if (firstQuestion == null) {
                                firstQuestion = new Question(msg.getContent());
                                System.out.println("Билет " + myAgent.getLocalName() + " выбрал в качестве первого вопроса " + msg.getSender().getLocalName() + " - " + firstQuestion.toString());
                                step = 0;

                            }
                        }
                        //в случае отказа от вопроса, возвращаемся к ожиданию сообщений
                        // в противном вообще случаем снова блокируемся
                        if (msg.getPerformative() == ACLMessage.REFUSE) {
                            firstQuestion = null;
                            step = 0;
                            System.out.println("Билету " + myAgent.getLocalName() + " отказали в первом вопросе" + msg.getSender().getLocalName());
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    // принимаем сообщения от вопросов только по шаблону Согласие или Отказ
                    mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.AGREE), MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
                    msg = myAgent.receive(mt);

                    if (msg != null) {
                        // проверяем незанятость поля для первого вопроса и проверяем второй вопроса на совпадение
                        // темы с первым вопросом в билете
                        if (msg.getPerformative() == ACLMessage.AGREE) {
                            if (secondQuestion == null && !firstQuestion.theme.equals((new Question(msg.getContent())).theme)) {
                                secondQuestion = new Question(msg.getContent());
                                System.out.println("Билет " + myAgent.getLocalName() + " выбрал в качестве второго вопроса " + msg.getSender().getLocalName() + " - " + secondQuestion.toString());
                                step = 0;
                            } else {
                                // иначе отправляем отмену Вопросу и Вопрос перерегиструется
                                secondQuestion = null;
                                step = 0;
                                ACLMessage reply = msg.createReply();
                                reply.setPerformative(ACLMessage.CANCEL);
                                reply.setContent("Отмена");
                                myAgent.send(reply);
                                System.out.println("Билет " + myAgent.getLocalName() + " отказал - темы не совпали" + msg.getSender().getLocalName());
                            }
                        }
                        //в случае Отказа возвращаеся к ожиданию приема вопросов
                        // иначе снова блокировка
                        if (msg.getPerformative() == ACLMessage.REFUSE) {
                            secondQuestion = null;
                            step = 0;
                            System.out.println("Билету " + myAgent.getLocalName() + " отказали во втором вопросе" + msg.getSender().getLocalName());
                        }

                    } else {
                        block();
                    }

            }
        }

        // поведение кончится когда первый вопрос или второй вопрос уже получен
        @Override
        public boolean done() {
            return (step == 1 && firstQuestion != null) || (step == 2 && secondQuestion != null);

        }


    }

    // получение инфомрации о средней сложности билетов и дальнейшее самоопределние
    private class Exchanger extends TickerBehaviour {

        int step;
        boolean isInitiator = false;
        MessageTemplate mt;
        ACLMessage msg;

        public Exchanger(Agent a, long period) {
            super(a, period);
            step = 0;
        }

        @Override
        protected void onTick() {
            //System.err.println("Запуск Exchanger");
            switch (step) {
                case 0:
                    mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM); //принимаем от менеджера сообщение о том, какая средняя сложность билета
                    msg = myAgent.receive(mt);

                    if (msg != null) {
                        average = Double.parseDouble(msg.getContent());
                        if (firstQuestion.complexity + secondQuestion.complexity > average) {
                            isInitiator = true;
                        }

                        try {
                            DFService.deregister(this.myAgent);
                        } catch (FIPAException fe) {
                            fe.printStackTrace();
                        }
                        //в зависимости от средней сложности меняем сервис билета - Initiator если >average, simple - если <average
                        DFAgentDescription dfd = new DFAgentDescription();
                        dfd.setName(getAID());
                        ServiceDescription sd = new ServiceDescription();
                        sd.setType(isInitiator ? "initiator" : "simple");
                        sd.setName("MyCard");
                        dfd.addServices(sd);
                        try {
                            DFService.register(this.myAgent, dfd);
                        } catch (FIPAException fe) {
                            fe.printStackTrace();
                        }

                        //отправляем менеджеру сообщение, что поменяли сервис
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.CONFIRM);
                        reply.setContent("Я поменял сервис");
                        System.err.println("Билет " + myAgent.getLocalName() + "Поменял сервис");
                        myAgent.send(reply);

                        step = 1;
                    } else {
                        block();
                    }
                    break;
                case 1:
                    // получаем сообщение от менеджера что можно начинать обмен поведение и ставим соответсвтующие поведения
                    mt = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM);
                    msg = myAgent.receive(mt);

                    if (msg != null) {
                        if (msg.getContent().equals("Начинайте обмен!")) {
                            if (isInitiator) {
                                Intiiator = new InitiatorRequester(this.myAgent, 1000);
                                System.err.println("Билет " + getLocalName() + " стал инициатором");
                                myAgent.addBehaviour(Intiiator);
                            } else {
                                Simple = new SimpleBehaviour();
                                myAgent.addBehaviour(Simple);
                            }
                            ((ExamCardAgent) myAgent).average = average;
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;

            }
        }

    }

    // поведение-инициатора для запроса вопросов от обычных
    private class InitiatorRequester extends TickerBehaviour {
        int step;
        int cnt;

        public InitiatorRequester(Agent a, long period) {
            super(a, period);
            step = 0;
            cnt = 0;
            simpleCards = new HashSet<>();
        }

        @Override
        protected void onTick() {
            if (isChangedInitiator) {
                removeBehaviour(this);
                return;
            }
            switch (step) {
                case 0:
                    //отправляем всем simple-билетам свои вопросы
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("simple");
                    template.addServices(sd);
                    System.err.println("Билет " + getLocalName() + " ищет простых cnt = " + cnt);
                    if (cnt == 0) {
                        try {
                            DFAgentDescription[] result = DFService.search(myAgent, template);
                            //countOfNotAnsweredSimples = result.length;
                            for (DFAgentDescription card : result) {
                                simpleCards.add(card.getName());
                            }
                            System.err.println("SimpleCards составлен у " + myAgent.getLocalName());
                        } catch (FIPAException fe) {
                            fe.printStackTrace();
                        }
                        if (simpleCards.size() == 0) {
                            System.err.println("SimpleCards нет у " + myAgent.getLocalName());
                            return;
                        }

                        ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
                        for (AID card : simpleCards) {
                            message.addReceiver(card);
                        }


                        message.setContent("Дай свои вопросы");
                        message.setReplyWith("request" + System.currentTimeMillis());
                        message.setLanguage("1");
                        myAgent.send(message);
                    }

                    if (cnt == 0) {
                        addBehaviour(new InitiatorBehaviour());
                        cnt++;
                        step = 1;
                    }
                case 1:
                    ACLMessage message1 = new ACLMessage(ACLMessage.REQUEST);
                    for (AID card : simpleCards) {
                        message1.addReceiver(card);
                    }
                    message1.setContent("Дай свои вопросы");
                    message1.setReplyWith("request" + System.currentTimeMillis());
                    message1.setLanguage("1");
                    myAgent.send(message1);

            }
        }

    }

    private class InitiatorBehaviour extends Behaviour {

        int step = 1;
        ACLMessage msg;
        MessageTemplate mt;
        MessageTemplate mtl = MessageTemplate.MatchLanguage("1");
        int count_of_refuses = 0;

        @Override
        public void action() {
            switch (step) {
                case 1:
                    // если не менялся
                    if (!isChangedInitiator) {
                        // получаем от обычных вопросы
                        mt = MessageTemplate.and(mtl, MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                        msg = myAgent.receive(mt);
                        if (msg != null) {
                            System.out.println(myAgent.getLocalName() + " получил вопросы от " + msg.getSender().getLocalName());
                            String[] split = msg.getContent().split(":");
                            if (split.length != 2) {
                                int k = 0;
                                k++;
                                System.err.println(msg.getSender().getLocalName());
                            }
                            Question q1 = new Question(split[0]);
                            Question q2 = new Question(split[1]);
                            Question[] qs = new Question[]
                                    {
                                            q1, q2, firstQuestion, secondQuestion
                                    };
                            // считаем разность по модулю сложности вопросов
                            int sum1 = qs[0].complexity + qs[1].complexity;
                            int sum2 = qs[2].complexity + qs[3].complexity;
                            int rez0 = Math.abs(sum1 - sum2);
                            // и их сочетаний
                            int rez1 = check(qs[0], qs[2], qs[1], qs[3]);
                            int rez2 = check(qs[1], qs[2], qs[0], qs[3]);
                            int rez3 = check(qs[1], qs[3], qs[0], qs[2]);
                            // и если разность по модулю сочетаний вопросов меньше чем разность по модулю сложностей
                            // в изначальном порядке
                            if (rez0 > rez1 || rez0 > rez2 || rez0 > rez3)
                                // выбираем наименьшую разность, а следовательно и сочетание
                                if (rez1 < rez2 && rez1 < rez3) {
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                    reply.setContent(qs[0].toString() + ":" + qs[2].toString() + ":" + qs[1].toString() + ":" + qs[3].toString());
                                    reply.setLanguage("1");
                                    myAgent.send(reply);
                                    step = 2;
                                    System.out.println(myAgent.getLocalName() + " предложил поменяться " + msg.getSender().getLocalName());

                                } else if (rez2 < rez1 && rez2 < rez3) {
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                    reply.setContent(qs[1].toString() + ":" + qs[2].toString() + ":" + qs[0].toString() + ":" + qs[3].toString());
                                    reply.setLanguage("1");
                                    myAgent.send(reply);
                                    step = 2;
                                    System.out.println(myAgent.getLocalName() + " предложил поменяться " + msg.getSender().getLocalName());
                                } else {
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                    reply.setContent(qs[1].toString() + ":" + qs[3].toString() + ":" + qs[0].toString() + ":" + qs[2].toString());
                                    reply.setLanguage("1");
                                    myAgent.send(reply);
                                    step = 2;
                                    System.out.println(myAgent.getLocalName() + " предложил поменяться " + msg.getSender().getLocalName());
                                }
                                // если нет, на нет и суда нет
                            else {

                                System.out.println("У " + myAgent.getLocalName() + " нет хороших вариантов обмена с " + msg.getSender().getLocalName() + " " + simpleCards.size());
                                simpleCards.remove(msg.getSender());
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (simpleCards.size() <= 0) //если нам ответили все simple-билеты
                                {
                                    step = 3;
                                    break;
                                }
                            }


                        } else {
                            block();
                        }
                    }
                    break;
                case 2:
                    if (!isChangedInitiator) {
                        // ждем сообщений по языку
                        mt = MessageTemplate.and(mtl, MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.REFUSE), MessageTemplate.MatchPerformative(ACLMessage.AGREE)));
                        msg = myAgent.receive(mt);
                        if (msg != null) {
                            //  simpleCards.remove(msg.getSender());
                            // если согласие от простого билета пришло
                            // принимаем новую комбинацию после того как забрал новую комбинацию обычный билет
                            if (msg.getPerformative() == ACLMessage.AGREE) {
                                System.out.println(msg.getContent());
                                String[] split = msg.getContent().split(":");
                                firstQuestion = new Question(split[0]);
                                secondQuestion = new Question(split[1]);
                                System.out.println(myAgent.getLocalName() + " поменялся с " + msg.getSender().getLocalName() + " " + simpleCards.size());
                                simpleCards.remove(msg.getSender());

                            }
                            if (msg.getPerformative() == ACLMessage.REFUSE) {
                                System.out.println(myAgent.getLocalName() + " отказали в обмене  с" + msg.getSender().getLocalName() + " " + simpleCards.size());
                                simpleCards.remove(msg.getSender());
                                /*count_of_refuses++;
                                if (count_of_refuses == simpleCards.size()) {
                                    System.err.println(myAgent.getLocalName() + " устал от отказов");
                                    step = 3;
                                    break;
                                }*/

                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                //при отказе в обмене вопросами снова ждем предложения о вопросах
                            }

                            if (simpleCards.size() <= 0) //если нам ответили все simple-билеты
                            {
                                step = 3;
                                break;
                            }
                            step = 1;
                        } else {
                            block();
                        }
                    }
                    break;
                case 3:

                    // если больше не осталось простых вопросов
                    // докладываемся менеджеру о завершении обмена
                    if (!isChangedInitiator) {
                        isChangedInitiator = true;
                        AID manager = null;
                        DFAgentDescription template = new DFAgentDescription();
                        ServiceDescription sd = new ServiceDescription();
                        sd.setType("manager");
                        template.addServices(sd);
                        try {
                            DFAgentDescription[] result = DFService.search(myAgent, template);
                            if (result.length != 0) {
                                manager = result[0].getName();
                            } else {
                                return;
                            }
                        } catch (FIPAException fe) {
                            fe.printStackTrace();
                        }
                        ACLMessage message = new ACLMessage(ACLMessage.INFORM_REF);
                        message.addReceiver(manager);
                        message.setContent("Обмен закончен");
                        message.setReplyWith("ready" + System.currentTimeMillis());
                        myAgent.send(message);
                        System.err.println("Билет " + getName() + " обменялся");

                        step = 4;
                    }
                    break;

                case 4:

                    // получаем требование от менелдера о раскрытии себя
                    mt = MessageTemplate.and(MessageTemplate.MatchLanguage("1"), MessageTemplate.MatchPerformative(ACLMessage.PROPAGATE));
                    msg = myAgent.receive(mt);
                    if (msg != null) {
                        int sum = firstQuestion.complexity + secondQuestion.complexity;
                        System.out.println("Билет инициатор " + myAgent.getLocalName() + " готов: " + ((ExamCardAgent) myAgent).firstQuestion.toString() + ", " + ((ExamCardAgent) myAgent).secondQuestion.toString() + " Сложность=" + sum);
                        step = 5;
                        // myAgent.doDelete();
                    }
                    break;
                case 5:
                    AID manager = null;
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("manager");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result.length != 0) {
                            manager = result[0].getName();
                        } else {
                            return;
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    ACLMessage message = new ACLMessage(ACLMessage.SUBSCRIBE);
                    message.addReceiver(manager);
                    message.setContent(String.valueOf((firstQuestion.complexity + secondQuestion.complexity)));
                    message.setLanguage("1");
                    myAgent.send(message);
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    step = 6;
                case 6:
                    mt = MessageTemplate.and(MessageTemplate.MatchLanguage("1"), MessageTemplate.MatchPerformative(ACLMessage.FAILURE));
                    msg = myAgent.receive(mt);
                    if (msg != null) {
                     /*   try {
                            DFService.deregister(myAgent);
                        } catch (FIPAException fe) {
                            fe.printStackTrace();
                        }*/
                        isChangedInitiator = false;
                        removeBehaviour(exchanger);
                        exchanger = new Exchanger(myAgent, 3000);
                        addBehaviour(exchanger);
                        removeBehaviour(Intiiator);

                    } else {
                        block();
                    }
            }
        }

        @Override
        public boolean done() {
            if (step == 7) {
                return true;
            }
            return false;
        }

        //сравниваются билеты по номеру в билете 1 вопрос 1-го и 2-го билета итд
        // проверяется совпадение тем и далее считается разность по модулю
        // общей сложности 1 и 2 по счету вопросов
        int check(Question q11, Question q12, Question q21, Question q22) {
            if (!q11.theme.equals(q12.theme) && !q21.theme.equals(q22.theme)) {
                int sum11 = q11.complexity + q12.complexity;
                int sum22 = q21.complexity + q22.complexity;
                {
                    return Math.abs(sum11 - sum22);
                }
            }
            return Integer.MAX_VALUE;
        }
    }

    // поведение обычного билета
    private class SimpleBehaviour extends CyclicBehaviour {

        ACLMessage msg;
        boolean isChanged = false;

        @Override
        public void action() {
            // ограничиываем язык сообщений
            msg = myAgent.receive(MessageTemplate.MatchLanguage("1"));
            if (msg != null) {
                //отправляем вопросы
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(firstQuestion.toString() + ":" + secondQuestion.toString());
                    myAgent.send(reply);
                    //если всё ОК и билет еще не менялся, принимаем от инициатора новые вопросы
                } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL && !isChanged) {
                    isChanged = true;
                    String[] split = msg.getContent().split(":");
                    firstQuestion = new Question(split[0]);
                    secondQuestion = new Question(split[1]);
                    ACLMessage reply = msg.createReply();
                    reply.setContent(split[2] + ":" + split[3]);
                    reply.setPerformative(ACLMessage.AGREE);
                    reply.setLanguage("1");
                    myAgent.send(reply);
                    System.out.println(myAgent.getLocalName() + " согласен меняться с " + msg.getSender().getLocalName());

                }
                // если уже менялись
                else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL && isChanged) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("У моих вопросов уже нормальная сложность");
                    reply.setLanguage("1");
                    myAgent.send(reply);
                    System.out.println(myAgent.getLocalName() + " НЕ согласен меняться с " + msg.getSender().getLocalName());

                }
                // получаем требование от менелдера о раскрытии себя
                if (msg.getPerformative() == ACLMessage.PROPAGATE) {
                    int sum = firstQuestion.complexity + secondQuestion.complexity;
                    System.out.println("Билет простой " + myAgent.getLocalName() + " готов: " + ((ExamCardAgent) myAgent).firstQuestion.toString() + ", " + ((ExamCardAgent) myAgent).secondQuestion.toString() + " Сложность=" + sum);
                    //myAgent.doDelete();
                    AID manager = null;
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("manager");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result.length != 0) {
                            manager = result[0].getName();
                        } else {
                            return;
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    ACLMessage message = new ACLMessage(ACLMessage.SUBSCRIBE);
                    message.addReceiver(manager);
                    message.setContent(String.valueOf((firstQuestion.complexity + secondQuestion.complexity)));
                    message.setLanguage("1");
                    myAgent.send(message);
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                if (msg.getPerformative() == ACLMessage.FAILURE) {
                  /*  try {
                        DFService.deregister(myAgent);
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }*/
                    isChanged = false;
                    removeBehaviour(exchanger);
                    exchanger = new Exchanger(myAgent, 3000);
                    addBehaviour(exchanger);
                    removeBehaviour(Simple);
                }
            } else {
                block();
            }
        }
    }


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

