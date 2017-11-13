package ele.me.hackathon.tank;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

import ele.me.hackathon.tank.Mock.MockA;
import ele.me.hackathon.tank.Mock.MockB;
import ele.me.hackathon.tank.player.Args;
import ele.me.hackathon.tank.player.Order;
import ele.me.hackathon.tank.player.PlayerServer;

/**
 * Created by lanjiangang on 27/10/2017.
 */
public class GameEngine {
    private GameStateMachine stateMachine;
    private Player playerA;
    private Player playerB;
    private int maxRound;
    private String mapFile;
    private GameMap map;
    private int noOfTanks;
    private int tankSpeed;
    private int shellSpeed;
    private int tankHP;
    private int tankScore;
    private int flagScore;
    private int roundTimeout;
    private String playerAAddres;
    private String playerBAddres;

    private static GameEngine instance = new GameEngine();
    private Map<String, PlayerServer.Client> clients;
    private Map<String, Player> players;
    private GameOptions gameOptions;

    public static void main(String[] args) throws TTransportException {

        GameEngine engine = GameEngine.getInstance();
        engine.parseArgs(args);
        engine.startGame();
    }

    private GameEngine() {
    }

    public static GameEngine getInstance() {
        return instance;
    }

    private void parseArgs(String[] args) {
        if (args.length == 0){
            String classPath = this.getClass().getClassLoader().getResource("").getPath();
            mapFile = classPath.split("target")[0] + "maps/firstweekmap.txt";
            noOfTanks = 4;
            tankSpeed = 1;
            shellSpeed = 3;
            tankHP = 5;
            tankScore = 3;
            flagScore = 5;
            maxRound = 99;
            roundTimeout = 100;
            this.gameOptions = new GameOptions(9, 1, 3, 5, 3, 5, 99, 100);
            playerAAddres = "127.0.0.1:10081";
            playerBAddres = "127.0.0.1:10082";
            return;
        }
        mapFile = args[0];
        noOfTanks = Integer.parseInt(args[1]);
        tankSpeed = Integer.parseInt(args[2]);
        shellSpeed = Integer.parseInt(args[3]);
        tankHP = Integer.parseInt(args[4]);
        tankScore = Integer.parseInt(args[5]);
        flagScore = Integer.parseInt(args[6]);
        maxRound = Integer.parseInt(args[7]);
        roundTimeout = Integer.parseInt(args[8]);
        playerAAddres = args[9];
        playerBAddres = args[10];
        this.gameOptions = new GameOptions(noOfTanks, tankSpeed, shellSpeed, tankHP, tankScore, flagScore, maxRound, roundTimeout);
        System.out.println("Parameters parsed. " + this.toString());
    }

    private void startGame() throws TTransportException {
        initGameStateMachine();
        this.clients = connectToPlayers();
        play();
    }

    private void initGameStateMachine() {
        map = loadMap(mapFile);
        Map<Integer, Tank> tanks = generateTanks();
        this.players = assignTankToPlayers(tanks);

        stateMachine = new GameStateMachine(tanks, map);
        stateMachine.setOptions(gameOptions);
        stateMachine.setPlayers(players);
    }

    private Map<String, Player> assignTankToPlayers(Map<Integer, Tank> tanks) {
        Map<String, Player> players = new LinkedHashMap<>();

        players.put(playerAAddres,
                new Player(playerAAddres, tanks.keySet().stream().filter(id -> id <= noOfTanks).collect(Collectors.toCollection(LinkedList::new))));
        players.put(playerBAddres,
                new Player(playerBAddres, tanks.keySet().stream().filter(id -> id > noOfTanks).collect(Collectors.toCollection(LinkedList::new))));
        return players;
    }

    private Map<Integer, Tank> generateTanks() {
        Map<Integer, Tank> tanks = new LinkedHashMap<>();
        int index = 0;
        int mapsize = map.size();
        int n = (int) Math.sqrt(noOfTanks);
        for (int x = 1; x < n + 1; x++) {
            for (int y = 1; y < n + 1; y++) {
                index++;
                tanks.put(index, new Tank(index, new Position(x, y), Direction.RIGHT, tankSpeed, shellSpeed, tankHP));
                tanks.put(index + noOfTanks,
                        new Tank(index + noOfTanks, new Position(mapsize - x - 1, mapsize - y - 1), Direction.RIGHT, tankSpeed, shellSpeed, tankHP));
            }
        }
        return tanks;
    }

    private void play() {
        List<PlayerInteract> actors = Arrays.asList(new String[] { playerAAddres, playerBAddres }).stream().map(name -> buildPlayerInteract(name, gameOptions))
                .collect(Collectors.toList());
        Map<String, LinkedBlockingQueue<List<TankOrder>>> tankOrderQueues = actors.stream()
                .collect(Collectors.toMap(PlayerInteract::getAddress, act -> act.getCommandQueue()));
        Map<String, LinkedBlockingQueue<GameState>> stateQueues = actors.stream()
                .collect(Collectors.toMap(PlayerInteract::getAddress, act -> act.getStatusQueue()));

        actors.forEach(act -> act.start());

        //send a singal tp upload map and tank list
        stateQueues.values().forEach(q -> q.offer(new GameState("fakeState")));
        int round = 0;
        for (; round < maxRound; round++) {
            System.out.println("Round " + round);
            //clear the command queue to prevent previous dirty command left in the queue
            tankOrderQueues.values().forEach(q -> q.clear());

            Map<String, GameState> latestState = stateMachine.reportState();
            latestState.entrySet().forEach(k -> stateQueues.get(k.getKey()).offer(k.getValue()));

            List<TankOrder> orders = new LinkedList<>();
            tankOrderQueues.values().forEach(q -> {
                try {
                    orders.addAll(q.take());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            orders.forEach(o -> System.out.println(o));
            stateMachine.newOrders(orders);

            if (stateMachine.gameOvered()) {
                break;
            }
        }

        Map<String, Integer> scores;
        if (round < maxRound) {
            scores = stateMachine.countScore(tankScore, 0);
        } else {
            scores = stateMachine.countScore(tankScore, flagScore);
        }

        if (scores.get(playerAAddres) > scores.get(playerBAddres)) {
            System.out.println(playerAAddres + " wins the game!");
        } else if (scores.get(playerAAddres) == scores.get(playerBAddres)) {
            System.out.println("Draw game!");
        } else {
            System.out.println(playerBAddres + " wins the game!");
        }
    }

    private PlayerInteract buildPlayerInteract(String name, GameOptions gameOptions) {
        return new PlayerInteract(name, clients.get(name), map, players.get(name).getTanks(), this.gameOptions);
    }

    private Map<String, PlayerServer.Client> connectToPlayers() throws TTransportException {
        Map<String, PlayerServer.Client> clients = new LinkedHashMap<>();
        clients.put(playerAAddres, buildPlayerConnection(playerAAddres));
        clients.put(playerBAddres, buildPlayerConnection(playerBAddres));
        return clients;
    }
    
    private PlayerServer.Client buildPlayerConnection(String addr) throws TTransportException {
        String host = addr.split(":")[0];
        int port = Integer.parseInt(addr.split(":")[1]);
        TSocket transport = new TSocket(host, port);
//        transport.open();
        transport.setTimeout(roundTimeout);
        TProtocol protocol = new TBinaryProtocol(transport);
        PlayerServer.Client client = new PlayerServer.Client(protocol){
            Mock m = port == 10081 ? new MockA(): new MockB();
            @Override
            public void uploadMap(List<List<Integer>> gamemap) throws TException {
                m.uploadMap(gamemap);
            }

            @Override
            public void uploadParamters(Args arguments) throws TException {
                m.uploadParamters(arguments);
            }

            @Override
            public void assignTanks(List<Integer> tanks) throws TException {
                m.assignTanks(tanks);
            }

            @Override
            public void latestState(ele.me.hackathon.tank.player.GameState state) throws TException {
                m.latestState(state);
            }

            @Override
            public List<Order> getNewOrders() throws TException {
                return m.getNewOrders();
            }
            
        };
        return client;
    }

    private GameMap loadMap(String fileName) {
        try {
            return GameMap.load(new FileInputStream(new File(fileName)));
        } catch (IOException e) {
            throw new RuntimeException("failed to load map file : " + fileName);
        }
    }
    

}
