package ele.me.hackathon.tank;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by lanjiangang on 08/11/2017.
 */
public class GameEngineTest {

    private static GameMap map;
    private GameStateMachine stateMachine;
    private GameOptions options;
    private GameEngine engine;

    @BeforeClass
    public static void beforeClass() throws Exception {
        map = GameMap.load(GameStateMachineTest.class.getResourceAsStream("/samplemap.txt"));
    }

    @Before
    public void setup() {
        options = new GameOptions(4, 1, 2, 2, 1, 1, 100, 2000);

        engine = new GameEngine();
        engine.setGameOptions(options);
        engine.setMap(map);

        stateMachine = new GameStateMachine(engine.generateTanks(), map);
        stateMachine.setOptions(options);

        engine.setStateMachine(stateMachine);
    }

    @Test
    public void testGenerateFlag() {
        for (int i = 0; i < options.getMaxRound(); i++) {
            engine.checkGenerateFlag(i);
        }
        assertEquals(options.getNoOfTanks(), engine.getNoOfFlagGenerated());
    }
}