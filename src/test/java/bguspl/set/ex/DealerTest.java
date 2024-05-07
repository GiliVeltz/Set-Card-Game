package bguspl.set.ex;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.mockito.Mock;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;

    private Player[] players;
    @Mock
    private Logger logger;
    @Mock
    private Env env;


    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        env = new Env(logger, new Config(logger, (String) null), ui, util);
        players = new Player[2];
        players[0] = new Player(env, dealer,table, 0,true);
        players[1] = new Player(env, dealer,table, 1,true);
        dealer = new Dealer(env, table, players);
    }

    void EmptyQueue(){
        dealer.toCheckQueue.clear();
    }


    @Test
    void addToCheckList(){
        EmptyQueue();

        int expectedPlayer = 0;

        dealer.addToCheckList(0);
        assertEquals(expectedPlayer, dealer.toCheckQueue.peek());
    }

    @Test
    void teminate(){
        boolean expectedTerminate = true;

        dealer.terminate();

        assertEquals(expectedTerminate, dealer.terminate);
    }

}