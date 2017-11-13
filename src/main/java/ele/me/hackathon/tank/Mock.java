package ele.me.hackathon.tank;

import java.util.ArrayList;
import java.util.List;

import ele.me.hackathon.tank.player.Args;
import ele.me.hackathon.tank.player.Order;

public abstract class Mock {

    public static class MockA extends Mock {
        @Override
        List<Order> getNewOrders() {
            System.out.println("A");
            // TODO Auto-generated method stub
            return new ArrayList<>();
        }

    }

    public static class MockB extends Mock {
        @Override
        List<Order> getNewOrders() {
            System.out.println("B");
            // TODO Auto-generated method stub
            return new ArrayList<>();
        }
    }
    
    
    ele.me.hackathon.tank.player.GameState state;
    List<List<Integer>> gamemap;
    List<Integer> tanks;
    Args arguments;
    

    public void setState(ele.me.hackathon.tank.player.GameState state) {
        this.state = state;
    }

    public void setGamemap(List<List<Integer>> gamemap) {
        this.gamemap = gamemap;
    }

    public void setTanks(List<Integer> tanks) {
        this.tanks = tanks;
    }

    public void setArguments(Args arguments) {
        this.arguments = arguments;
    }

    public void uploadMap(List<List<Integer>> gamemap) {
        setGamemap(gamemap);
    }

    public void latestState(ele.me.hackathon.tank.player.GameState state) {
        setState(state);
    }

    public void assignTanks(List<Integer> tanks) {
        setTanks(tanks);
    }

    public void uploadParamters(Args arguments) {
        setArguments(arguments);
    }

    abstract List<Order> getNewOrders();
}
