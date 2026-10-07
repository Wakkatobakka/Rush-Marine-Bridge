import com.wakka.bridge.SessionGate;
import com.wakka.rushbridge.BrewInputRouter;
import com.wakka.rushbridge.runtime.BrewRuntime;
import java.util.ArrayList;
import java.util.List;

/** Guards ownership/lifecycle contracts introduced by the shared-shell migration. */
public final class BehaviorChecks {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        List<String> events=new ArrayList<>();
        BrewInputRouter input=new BrewInputRouter(new BrewInputRouter.Sink(){
            public void press(int code){events.add("P:"+Integer.toHexString(code));}
            public void release(int code){events.add("R:"+Integer.toHexString(code));}
            public void tap(int code){events.add("T:"+Integer.toHexString(code));}
        });
        check(input.mask()==0&&events.isEmpty(),"construction must not synthesize AUTO or any guest input");
        input.setKey(1001,BrewRuntime.AVK_SELECT);input.setKey(1,BrewRuntime.AVK_SELECT);input.setKey(-97,BrewRuntime.AVK_SELECT);
        check(events.toString().equals("[P:e035]"),"touch/keypad/hardware overlap produces one native FIRE press");
        input.remove(1001);input.remove(1);
        check(events.size()==1&&input.contacts()==1,"lifting two contacts must not release hardware FIRE");
        input.remove(-97);check(events.toString().equals("[P:e035, R:e035]"),"last owner produces the native release");
        events.clear();input.setKey(1000,BrewRuntime.AVK_UP);input.setKey(1000,BrewInputRouter.sectorKey(7));
        check(events.toString().equals("[P:e031, R:e031, P:e024]"),"UP to UP-RIGHT releases first and emits one dedicated diagonal");
        check(input.mask()==BrewInputRouter.maskFor(BrewRuntime.AVK_3),"diagonal must not hold two cardinal keys");
        input.setKey(1001,BrewRuntime.AVK_SELECT);input.clear();
        check(input.mask()==0&&input.contacts()==0,"focus loss clears every owner");
        check(events.subList(events.size()-2,events.size()).toString().equals("[R:e024, R:e035]"),"focus loss releases direction and FIRE");
        events.clear();check(input.tap(BrewRuntime.AVK_0),"explicit AUTO uses native pulse");
        check(events.toString().equals("[T:e021]")&&input.mask()==0,"AUTO pulse creates no sticky held key");
        input.setKey(-7,BrewRuntime.AVK_0);check(!input.tap(BrewRuntime.AVK_0),"AUTO pulse cannot prematurely release another owner's held zero");
        input.remove(-7);check(events.get(events.size()-1).equals("R:e021"),"numeric hardware owner releases its captured code");
        input.setKey(17,0);check(input.contacts()==0,"unverified handset slots stay inert");
        SessionGate gate=new SessionGate();gate.foreground(true);gate.userPaused(true);gate.foreground(false);gate.foreground(true);
        check(gate.blocked(),"foreground return must preserve explicit pause");gate.userPaused(false);check(!gate.blocked(),"explicit resume unblocks foreground play");
        gate.foreground(false);check(gate.blocked(),"background is an independent pause reason");
        System.out.println("PASS: "+checks+" ownership and pause assertions");
    }
}
