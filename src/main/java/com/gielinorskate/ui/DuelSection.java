package com.gielinorskate.ui;

import static com.gielinorskate.ui.Widgets.*;

import com.gielinorskate.duel.DuelLines;
import com.gielinorskate.duel.DuelStateMachine.Phase;
import com.gielinorskate.duel.DuelView;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
* The side panel's "Skate Duel" section: this account's win / loss record, what is going on, Accept / Decline
* for a challenge received, and the party members skating with duels on, each with a Challenge button. Swing
* only (EDT): it is told what to show ({@link #display}) and asks for actions through its callbacks.
*/
public class DuelSection extends JPanel
{
/** What the buttons ask for (the plugin hops to the client thread). */
public interface Actions
{
void challenge(long memberId);

void accept();

void decline();

void withdraw();
}

private final Actions actions;
private final JLabel record = greyLabel(null);
private final JLabel status = greyLabel(null);
final JPanel answer = left(dark(new JPanel(new GridLayout(1, 2, 4, 0))));
final JButton accept;
final JButton decline;
private final JButton withdraw;
final JPanel list = column();
/** The members whose rows are showing, and whether their buttons were enabled. */
private List<DuelView.Member> shownMembers = List.of();
private boolean shownCanChallenge;

public DuelSection(Actions actions)
{
this.actions = actions;
setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
dark(this);

add(heading("Skate Duel"));
add(record);
status.setBorder(new EmptyBorder(2, 0, 4, 0));
add(status);

accept = button("Accept", null, e -> actions.accept());
decline = button("Decline", null, e -> actions.decline());
answer.add(accept);
answer.add(decline);
stretch(answer, 28);
add(answer);

withdraw = button("Take back challenge", null, e -> actions.withdraw());
stretch(withdraw, 28);
add(left(withdraw));
add(list);

display(null);
}

/** Shows {@code v}; null before anything is known. */
public void display(DuelView v)
{
if (v == null)
{
record.setText(DuelLines.record(0, 0));
status.setText(html("Join a RuneLite party to duel party members who are skating."));
answer.setVisible(false);
withdraw.setVisible(false);
showMembers(List.of(), false);
return;
}
record.setText(DuelLines.record(v.wins, v.losses));
status.setText(html(esc(v.status())));
boolean challenged = v.allowed && v.phase == Phase.CHALLENGED;
answer.setVisible(challenged);
accept.setEnabled(challenged && v.skating);
accept.setToolTipText(v.skating ? null : "Start skating first");
withdraw.setVisible(v.phase == Phase.CHALLENGING);
boolean listed = v.allowed && v.inParty && (v.phase == Phase.IDLE || v.phase == Phase.OVER);
showMembers(listed ? v.members : List.of(), v.canChallenge());
}

/** Rebuilds the member rows only when they change. */
private void showMembers(List<DuelView.Member> members, boolean canChallenge)
{
if (members.equals(shownMembers) && canChallenge == shownCanChallenge)
return;
shownMembers = members;
shownCanChallenge = canChallenge;
list.removeAll();
for (DuelView.Member m : members)
{
JPanel row = left(new JPanel(new BorderLayout(4, 0)));
row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
row.setBorder(new EmptyBorder(3, 6, 3, 3));
row.add(greyLabel(m.name), BorderLayout.CENTER);
JButton b = button("Challenge", null, e -> actions.challenge(m.id));
b.setEnabled(canChallenge);
b.setToolTipText(canChallenge ? "Challenge " + m.name + " to a Skate Duel" : "Start skating first");
row.add(b, BorderLayout.EAST);
stretch(row, 30);
list.add(row);
}
list.revalidate();
list.repaint();
}
}
