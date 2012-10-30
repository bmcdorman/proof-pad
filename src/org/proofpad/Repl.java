package org.proofpad;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.Token;
import org.proofpad.Acl2.OutputEvent;
import org.proofpad.SExpUtils.ExpType;


public class Repl extends JPanel {

	static final Icon infoIcon = new ImageIcon(Repl.class.getResource("/media/info.png"));
	static final Icon promptIcon = new ImageIcon(Repl.class.getResource("/media/prompt.png"));
	static final ImageIcon moreIcon = new ImageIcon(Repl.class.getResource("/media/more.png"));

	public interface HeightChangeListener {
		public void heightChanged(int delta);
	}


	public class StatusLabel extends JLabel {
		private static final long serialVersionUID = -6292618935259682146L;
		static final int size = ProofBar.width;
		public StatusLabel(MsgType msg) {
			setHorizontalAlignment(CENTER);
			setMsgType(msg);
			setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));
			setBackground(Color.WHITE);
			setOpaque(true);
			setMinimumSize(new Dimension(size, size));
			setMaximumSize(new Dimension(size, Short.MAX_VALUE));
			setPreferredSize(new Dimension(size, size));
		}
		public StatusLabel() {
			this(null);
		}
		public void setMsgType(MsgType m) {
			if (m == null) return;
			switch(m) {
			case ERROR:
				setIcon(ProofBar.errorIcon);
				break;
			case INPUT:
				setIcon(promptIcon);
				break;
			case INFO:
				setIcon(infoIcon);
				break;
			case SUCCESS:
				setIcon(ProofBar.successIcon);
				break;
			}
		}
	}
	
	public class Pair<T, U> {
		public final T first;
		public final U second;

		public Pair(T first, U second) {
			this.first = first;
			this.second = second;
		}
	}
	
	private static final long serialVersionUID = -4551996064006604257L;
	private static final int MAX_BOTTOM_HEIGHT = 100;
	final Acl2 acl2;
	private JPanel output;
	JScrollBar vertical;
	final ArrayList<String> history;
	private CodePane definitions;
	protected int historyIndex = 0;
	boolean addedInputToHistory = false;
	private Font font;
	private final List<JComponent> fontChangeList = new LinkedList<JComponent>();
	CodePane input;
	JScrollPane inputScroller;
	private JSplitPane split;
	private HeightChangeListener heightChangeListener;
	private JPanel bottom;
	IdeWindow parent;
	protected JButton run;
	private int oldNeededHeight = 26;
		
	enum MsgType {
		ERROR,
		INPUT,
		INFO,
		SUCCESS
	}
	
	public Repl(final IdeWindow parent, Acl2 newAcl2, final CodePane definitions) {
		super();
		this.parent = parent;
		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setDividerSize(0);
		split.setResizeWeight(1);
		setLayout(new BorderLayout());
		add(split, BorderLayout.CENTER);
		acl2 = newAcl2;
		acl2.setOutputEventListener(new Acl2.OutputEventListener() {
			@Override
			public void handleOutputEvent(OutputEvent e) {
				displayResult(e.output, e.type);
			}
		});
		this.definitions = definitions;
		setBackground(Color.WHITE);
		setOpaque(true);
		history = new ArrayList<String>();
		output = new JPanel();
		output.setLayout(new BoxLayout(output, BoxLayout.Y_AXIS));
		output.setBackground(Color.WHITE);
		font = new Font("Monospaced", Font.PLAIN, 14);
		output.setFont(font);
		final JScrollPane scroller = new JScrollPane(getOutput());
		vertical = scroller.getVerticalScrollBar();
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.setPreferredSize(new Dimension(500,100));
		split.setTopComponent(scroller);
		bottom = new JPanel();
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
		bottom.setBackground(Color.WHITE);
		JLabel prompt = new StatusLabel(MsgType.INPUT);
		input = new CodePane(null);
		input.setDocument(new IdeDocument(null));
		prompt.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				input.requestFocus();
			}
		});
		bottom.add(prompt);
		inputScroller = new JScrollPane(input);
		inputScroller.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		inputScroller.setMaximumSize(new Dimension(Integer.MAX_VALUE, StatusLabel.size + 6));
		bottom.add(inputScroller);
		run = new JButton("run");
		run.setEnabled(false);
		run.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				runInputCode();
				Main.userData.addUse("runButton", false);
			}
		});
		input.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				maybeEnableButtons();
				if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && input.getText().equals("")) {
					// Prevent that awful backspace beep.
					e.consume();
				}
				if (e.getKeyCode() == KeyEvent.VK_UP) {
					if (input.getCaretLineNumber() != 0) {
						return;
					}
					// TODO: Flash background color or something to indicate that the contents has
					// changed? Maybe?
					if (!input.getText().equals("") && !addedInputToHistory) {
						history.add(input.getText());
						addedInputToHistory = true;
					}
					if (historyIndex > 0) {
						historyIndex--;
						String historyEntry = history.get(historyIndex);
						input.setText(historyEntry);
						adjustBottomHeight();
						input.setCaretPosition(0);
					}
				} else if (e.getKeyCode() == KeyEvent.VK_DOWN){
					if (input.getCaretLineNumber() != input.getLineCount() - 1) {
						return;
					}
					if (historyIndex < history.size()) {
						historyIndex++;
						if (historyIndex == history.size()) {
							resetInput();
						} else {
							String historyEntry = history.get(historyIndex);
							input.setText(historyEntry);
							adjustBottomHeight();
							input.setCaretPosition(input.getText().length());
						}
					}
				}
			}
			@Override
			public void keyTyped(KeyEvent e) {
				adjustBottomHeight();
				if (e.getKeyChar() == '\n') {
					int parenLevel = 0;
					for (Token t : input) {
						if (t == null || t.type == Token.NULL) {
							break;
						}
						if (t.isSingleChar('(')) {
							parenLevel++;
						} else if (t.isSingleChar(')')) {
							parenLevel--;
						}
					}
					if (parenLevel <= 0) {
						Main.userData.addUse("runButton", true);
						e.consume();
						runInputCode();
					}
				}
				maybeEnableButtons();
			}
		});
		bottom.add(run);
		// TODO: Fix tracing
		//bottom.add(trace);
		JPanel bottomWrapper = new JPanel();
		bottomWrapper.setLayout(new BorderLayout());
		bottomWrapper.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		bottomWrapper.add(bottom, BorderLayout.CENTER);
		split.setBottomComponent(bottomWrapper);
	}
	
	protected void adjustBottomHeight() {
		int neededHeight = (int)input.getPreferredScrollableViewportSize().getHeight() + 2;
		if (inputScroller.getHorizontalScrollBar().isVisible()) {
			neededHeight += inputScroller.getHorizontalScrollBar().getHeight();
		}
		neededHeight = (neededHeight < MAX_BOTTOM_HEIGHT ? neededHeight : MAX_BOTTOM_HEIGHT);
		split.setDividerLocation(split.getHeight() - neededHeight - 12);
//		bottom.setSize(bottom.getWidth(), neededHeight);
//		int oldScrollerHeight = inputScroller.getHeight();
		Dimension newScrollSize = new Dimension(Short.MAX_VALUE, neededHeight);
		inputScroller.setMaximumSize(newScrollSize);
		fireHeightChangedEvent(neededHeight - oldNeededHeight);
		oldNeededHeight = neededHeight;
	}
	
	protected void fireHeightChangedEvent(int delta) {
		if (delta == 0) return;
		if (heightChangeListener != null) {
			heightChangeListener.heightChanged(delta);
		}
	}

	void runInputCode() {
		List<Expression> exps = SExpUtils.topLevelExps((RSyntaxDocument) input.getDocument());
		if (exps.size() > 0 && exps.get(0).firstType == ExpType.UNDOABLE) {
			displayResult("This event was moved up to the definitions window.", MsgType.INFO);
			definitions.admitBelowProofLine(input.getText());
			resetInput();
			return;
		}
		displayResult(input.getText() + "\n", MsgType.INPUT);
		acl2.admit(input.getText(), null);
		history.add(input.getText().trim());
		if (history.size() > 500) {
			for (int i = history.size(); i > 400; i--) {
				history.remove(i);
			}
		}
		historyIndex = history.size();
		addedInputToHistory = false;
		resetInput();
	}

	public JPanel getOutput() {
		return output;
	}
	
	void resetInput() {
		input.setText("");
		adjustBottomHeight();
	}

	private static Pattern welcomeMessage = Pattern.compile(".*ACL2 comes with ABSOLUTELY NO " +
			"WARRANTY\\..*");
	private static Pattern guardViolation = Pattern.compile("ACL2 Error in TOP-LEVEL: The guard " +
			"for the function call (.*?), which is (.*?), is violated by the arguments in the " +
			"call (.*?)\\. See :DOC set-guard-checking for information.*");
	private static Pattern globalVar = Pattern.compile("ACL2 Error in TOP-LEVEL: Global " +
			"variables, such as (.*?), are not allowed. See :DOC ASSIGN and :DOC @.");
	private static Pattern wrongNumParams = Pattern.compile("ACL2 Error in TOP-LEVEL: (.*?) " +
			"takes (.*?) arguments? but in the call (.*?) it is given (.*?) arguments?\\..*");
	private static Pattern nonRec = Pattern.compile("Since (.*?) is non-recursive, its admission " +
			"is trivial\\..*");
	private static Pattern trivial = Pattern.compile("The admission of (.*?) is trivial, using " +
			"the relation O< .*");
	private static Pattern admission = Pattern.compile("For the admission of (.*?) we will use " +
			"the relation O< .*");
	private static Pattern proved = Pattern.compile("Q.E.D.");
	private static Pattern undefinedFunc = Pattern.compile("ACL2 Error in TOP-LEVEL: The symbol " +
			"(.*?) \\(in package \"ACL2\"\\) has neither a function nor macro definition in " +
			"ACL2\\. Please define it\\..*");
	
	public static List<String> cleanUpMsg(String result) {
		return cleanUpMsg(result, null, null);
	}
	
	private static List<String> cleanUpMsg(String result, Set<String> functions, MsgType msgtype) {
		List<String> ret = new ArrayList<String>();
		Matcher match;
		String joined = result.replaceAll("[\n\r]+", " ").replaceAll("\\s+", " ").trim();
		if ((match = welcomeMessage.matcher(joined)).matches()) {
			Main.userData.addReplMsg("welcomeMessage");
			ret.add("ACL2 started successfully.");
		}
		if ((match = guardViolation.matcher(joined)).matches()) {
			ret.add("Guard violation in " + match.group(3).toLowerCase() + ".");
			Main.userData.addReplMsg("guardViolation");
		}
		if ((match = globalVar.matcher(joined)).matches()) {
			ret.add("Global variables, such as " + match.group(1).toLowerCase() +
					", are not allowed.");
			Main.userData.addReplMsg("globalVar");
		}
		if ((match = wrongNumParams.matcher(joined)).matches()) {
			ret.add(match.group(1).toLowerCase() +  " takes " + match.group(2) +
					" arguments but was given " + match.group(4) + " at " +
					match.group(3).toLowerCase());
			Main.userData.addReplMsg("wrongNumParams");
		}
		if ((match = trivial.matcher(joined)).matches() ||
				   (match = nonRec.matcher(joined)).matches() ||
				   (match = admission.matcher(joined)).matches()) {
			if (msgtype == MsgType.ERROR) {
				ret.add("Admission of " + match.group(1).toLowerCase() + " failed.");
				Main.userData.addReplMsg("admissionFailed");
			} else {
				ret.add(match.group(1).toLowerCase() + " was admitted successfully.");
				Main.userData.addReplMsg("admissionSucceeded");
			}
		}
		if ((match = undefinedFunc.matcher(joined)).matches()) {
			String func = match.group(1).toLowerCase();
			ret.add("The function " + func + " is undefined.");
			Main.userData.addReplMsg("undefinedFunc");
		}
		if ((match = proved.matcher(joined)).find()) {
			ret.add("Proof successful.");
			Main.userData.addReplMsg("proofSuccess");
		}
		if (joined.length() > 70) {
			ret.add(joined.substring(0, 67) + " ...");
			Main.userData.addReplMsg(ret.get(ret.size() - 1));
		}
		if (ret.isEmpty()) {
			ret.add(joined);
			Main.userData.addReplMsg(joined);
		}
		return ret;
	}

	public void displayResult(final String result, MsgType type) {
		String traceFreeResult = result.replaceAll("\\s*\\d+>.*?\\n", "").replaceAll("\\s*<\\d+.*?\\n", "");
		String shortResult = cleanUpMsg(traceFreeResult,
				((Acl2Parser) definitions.getParser(0)).functions,
				type).get(0);
		if (shortResult.startsWith("ACL2 started successfully")) {
			type = MsgType.INFO;
		}
		final JPanel line = new JPanel();
		line.setPreferredSize(new Dimension(200, 25));
		line.setMaximumSize(new Dimension(Short.MAX_VALUE, 25));
		line.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
		JTextArea text = new JTextArea(shortResult.trim());
		text.setBorder(null);
		text.setEditable(false);
		text.setBackground(Color.LIGHT_GRAY);
		text.setOpaque(false);
		text.setFont(definitions.getFont());
		line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
		line.setBackground(Color.WHITE);
		StatusLabel status = new StatusLabel();
		status.setFont(definitions.getFont());
		synchronized(fontChangeList) {
			fontChangeList.add(status);
			fontChangeList.add(text);
		}

		text.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));
		line.add(status);
		status.setMsgType(type);
		switch (type) {
		case ERROR:
			line.setBackground(ProofBar.ERROR_COLOR);
			status.setBackground(ProofBar.ERROR_COLOR);
			status.setFont(status.getFont().deriveFont(18f));
			break;
		case INFO:
			break;
		case SUCCESS:
			status.setBackground(ProofBar.ADMITTED_COLOR);
			status.setFont(status.getFont().deriveFont(18f));
			break;
		case INPUT:
			status.setForeground(Color.GRAY);
			text.setForeground(Color.GRAY);
			break;
		}
		text.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		line.add(text);
		if (!shortResult.equals(result.trim())) {
			line.add(new JLabel(moreIcon));
			MouseListener ml = new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent arg0) {
					// TODO: Highlight the currently selected item and reset it in Runnable after.
					parent.setPreviewText(result, null);
				}
			};
			line.addMouseListener(ml);
			text.addMouseListener(ml);
		}
		synchronized (this) {
			getOutput().add(line);
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				line.scrollRectToVisible(new Rectangle(line.getLocation(), line.getSize()));
				vertical.setValue(vertical.getMaximum());
			}
		});
	}
	

	@Override
	public void setFont(Font f) {
		super.setFont(f);
		if (fontChangeList == null) return;
		synchronized (fontChangeList) {
			for (JComponent c : fontChangeList) {
				c.setFont(f);
			}
		}
		input.setFont(f);
		int size = (25 - input.getLineHeight()) / 2;
		input.setBorder(BorderFactory.createEmptyBorder(size, 10, size, size));
	}

	public void setHeightChangeListener(HeightChangeListener heightChangeListener) {
		this.heightChangeListener = heightChangeListener;
	}

	public int getInputHeight() {
		return inputScroller.getHeight();
	}

	void maybeEnableButtons() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				boolean enable = input.getLastVisibleOffset() != 0;
				run.setEnabled(enable);
			}
		});
	}

}
