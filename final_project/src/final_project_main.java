import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.Scanner;
import java.io.*;

public class final_project_main extends JFrame{
	private Font monoFont;
	
	public final_project_main() {
		setTitle("Assembly interpreter");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        // set font 
        monoFont = new Font("monospaced", Font.PLAIN, 15);
        
        // write assembly
        JTextArea asmArea = new JTextArea();
        asmArea.setFont(monoFont);
        JScrollPane asmScroll = new JScrollPane(asmArea);
        
        // result
        JTextArea result = new JTextArea();
        result.setFont(monoFont);
        JScrollPane resultScroll = new JScrollPane(result);
        
        // stack 
        JTextArea stackArea = new JTextArea();
        stackArea.setBackground(Color.BLACK);
        stackArea.setForeground(Color.GREEN);
        stackArea.setFont(monoFont);
        JScrollPane stackScroll = new JScrollPane(stackArea);
        
        // split 3 part (write asm, result, stack) 
        JSplitPane splitHorizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, resultScroll, stackScroll);
        splitHorizontal.setResizeWeight(0.5);
        JSplitPane splitVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, asmScroll, splitHorizontal);
        splitVertical.setResizeWeight(0.5);
        c.add(splitVertical);
        

        // run button
        JButton runButton = new JButton("Run!");
        runButton.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
    			String asm = asmArea.getText();
    			AsmInterpreter interpreter = new AsmInterpreter();
    			try {
    				interpreter.executeAsm(asm);
    				result.setText(interpreter.show_register() + interpreter.output);
    				stackArea.setText(interpreter.stack_status);
    				if(interpreter.output.equals("exit")) {
    					System.exit(0);
    				}
    			}
    			catch(Exception err) {
    				result.setText("Error:" + err.getMessage());
    			}
    		}
        });
        c.add(runButton, BorderLayout.SOUTH);

        setSize(800, 600);
        setVisible(true);
	}
	class AsmInterpreter {
		//  register(HashMap), argv(stack is not data structure, it means area!)
		private HashMap<String, Long> register = new HashMap<String, Long>();
		private Vector<Long>stack = new Vector<Long>();
		private String output = "", stack_status;
		
		// set register which saves argv before stack, rsp register
		public AsmInterpreter() {
			register.put("rax", (long)-1);
			register.put("rbx", (long)0);
			register.put("rcx", (long)0);
			register.put("rdx", (long)0);
			register.put("rsi", (long)0);
			register.put("rdi", (long)0);
			register.put("rsp", (long)0);
		}
		
		public void executeAsm(String asm) {
			stack.clear();
			String[] line = asm.split("\n");
			for(int i=0; i<line.length; i++) {
				String code = line[i];
				code.trim();
				// empty line
				if(code.equals("")) continue;
				String[] part = code.split(" ");
				if(part[0].equals("syscall")) {
					output = systemcall();
					continue;
				}				
				part[1] = part[1].replace(",", "");
				if(part[0].equals("mov")) move(part[1], part[2]);
				else if(part[0].equals("xor")) xor(part[1], part[2]);
				else if(part[0].equals("add")) add(part[1], part[2]);
				else if(part[0].equals("sub")) sub(part[1], part[2]);
				else if(part[0].equals("print")) print(part[1]);
				else if(part[0].equals("push") && part.length==2) {
					push(part[1]);
					stack_status = show_stack();
				}
				else if(part[0].equals("pop") && part.length==2) {
					pop(part[1]);
					stack_status = show_stack();
				}
				else throw new IllegalArgumentException("Invalid instruction!\n");
			}
		}
		// syscall function
		public String systemcall() {
			Long val = register.get("rax");
			StringBuilder filepath_str = new StringBuilder();
			String filename = "";
			
			// rdi is 64bit register -> only 8 word (!= WORD)
			// read -> file will read my input
			// rax = 0, rdi = file_descriptor, rsi = buf, rdx = size(count)
			long filepath_long = getValue("rdi");
//			while(filepath_long!=0) {
//				long num = filepath_long % 100;
//				filename += String.valueOf(num);
//				num /= 100;
//			}
			while(filepath_long!=0) {
				char num = (char)(filepath_long & 0xFF);
				filepath_str.append(num);
				filepath_long >>= 8;
			}
			filename = filepath_str.toString();
			System.out.println(filename);
			long sz = getValue("rdx");
			if(val == 0) {
				read_file(filename, (int)sz);
				return "File name: " + filename;
			}
			// write -> file will print the contents to my screen
			// rax = 1, rdi = file descriptor, rsi = buf, rdx = size(count)
			else if(val == 1) {
				write_file(filename, (int)sz);
				return "File name: " + filename;
			}
			// exit function
			else if(val == 60) {
				return "exit";
			}
			else throw new IllegalArgumentException("No exist value");
		}
		public boolean check_val(String reg) {
			if(register.get(reg)!=null) return true;
			else return false;
		}
		// move instruction
		public void move(String dest, String src) {
			if(check_val(dest)) {
				long val = getValue(src);
				register.put(dest, val);				
			}
			else throw new IllegalArgumentException("No register exist");
		}
		// xor instruction
		public void xor(String dest, String src) {
			if(check_val(dest)) {
				long val_src = getValue(src);
				long val_dest = getValue(dest);
				val_dest ^= val_src;
				register.put(dest, val_dest);
			}
			else throw new IllegalArgumentException("No register exist");
		}
		// additional instruction
		public void add(String dest, String src) {
			if(check_val(dest)) {			
				long val_src = getValue(src);
				long val_dest = getValue(dest);
				val_dest += val_src;
				register.put(dest, val_dest);			
			}
			else throw new IllegalArgumentException("No register exist");
		}
		// substract instruction
		public void sub(String dest, String src) {
			if(check_val(dest)) {
				long val_src = getValue(src);
				long val_dest = getValue(dest);
				val_dest -= val_src;
				register.put(dest, val_dest);
			}
			else throw new IllegalArgumentException("No register exist");
		}
		// push into stack instruction
		public void push(String reg) {
			long val = getValue(reg);
			if(register.get(reg)!=null) register.put(reg, val);
			register.put("rsp", val);
			stack.add(val);
		}
		// pop from stack to register instruction
		public void pop(String reg) {
			check_val(reg);
			if(stack.isEmpty()) {
				throw new IndexOutOfBoundsException("Stack is empty!\n");
			}
			long val = stack.lastElement();
			stack.removeLast();
			register.put(reg, val);
			if(!stack.isEmpty()) val = stack.lastElement();
			else val = 0;
			register.put("rsp", val);
		}
		// print instruction
		public void print(String reg) {
			if(reg.equals("rsp")) {
				long cnt = getValue("rcx");
				long data = getValue("rdi");
				StringBuilder c = new StringBuilder();
				while(data!=0) {
					char num = (char)(data & 0xFF);
					c.append(num);
					data >>= 8;
				}
				for(long i=0; i<cnt; i++) {
					output+=c;
				}
			}
			else throw new IllegalArgumentException("Return value exists in rax register!");
		}
		// show present stack status
		public String show_stack() {
			StringBuilder sb = new StringBuilder();
			sb.append("HIGH ADDRESS\n");
			String block = " ------------------";
			for(Long val : stack) {
				String v = "0x" + String.format("%02x",val);
				String s = String.format("%" + (block.length()-1) + "s", v);
				sb.append(block);
				sb.append("\n");
				sb.append("|");
				sb.append(s);
				sb.append("|");
				sb.append("\n");
			}
			sb.append(block);
			sb.append("\n");
			sb.append("LOW ADDRESS\n");
			return sb.toString();
		}
		// get register's value
		private long getValue(String op2) {
			// operand2 is register
			if(register.get(op2) != null) {
				return register.get(op2);
			}
			// operand2 is immediate value (long type)
			else {
				return Long.parseLong(op2, 16);
			}
		}
		// show present stack status
		public String show_register() {
			StringBuilder sb_r = new StringBuilder();
			for(String s: register.keySet()) {
				sb_r.append(s);
				sb_r.append(" = 0x");
				sb_r.append(Long.toHexString(register.get(s)));
				sb_r.append("\n");
			}
			return sb_r.toString();
		}
		// read_file => computer will read, so it will wait our input
		public void read_file(String filename, int sz) { 
			JFrame readframe = new JFrame();
			readframe.setTitle("Write file");
			readframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			
			Container c2 = readframe.getContentPane();
			c2.setLayout(new BorderLayout());
			
			JTextArea ta = new JTextArea();
	        JScrollPane taScroll = new JScrollPane(ta);
	        c2.add(taScroll, BorderLayout.CENTER);
	        
	        JPanel button = new JPanel();
	        button.setLayout(new FlowLayout(FlowLayout.CENTER));
	        
	        JButton save = new JButton("Save");
	        JButton cancel = new JButton("Cancel");
	        button.add(save);
	        button.add(cancel);
	        c2.add(button, BorderLayout.SOUTH);
	        
	        save.addActionListener(new ActionListener(){
	        	public void actionPerformed(ActionEvent e) {
	        		String text = ta.getText();
	        		// type casting
	        		System.out.println(text.length());
	        		// cut automatically
	        		if(text.length()>sz) {
	        			text = text.substring(0, sz);
	        		}
	        		System.out.println(text.length());
	        		try {
	        			BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
						bw.write(text);
						bw.close();
						JOptionPane.showMessageDialog(null, "Save Success", "Message", JOptionPane.INFORMATION_MESSAGE);
						readframe.dispose();
	        		}
	        		catch(IOException e2) {
						JOptionPane.showMessageDialog(null, "Error", "Alert", JOptionPane.ERROR_MESSAGE);
	    				readframe.dispose();
	    				throw new IllegalArgumentException("Save Failed!");
	        		}
	        	}
	        });
	        cancel.addActionListener(new ActionListener(){
	        	public void actionPerformed(ActionEvent e) {
	        		JOptionPane.showMessageDialog(null, "Close", "Message", JOptionPane.INFORMATION_MESSAGE);
	    			readframe.dispose();
	        	}
	        });
	        	        
	        readframe.setSize(400, 300);
	        readframe.setVisible(true);
		}
		// write_file => computer writes to screen
		public void write_file(String filename, int sz) {
			JFrame writeframe = new JFrame();
			writeframe.setTitle("Read file");
			writeframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			
			Container c3 = writeframe.getContentPane();
			c3.setLayout(new BorderLayout());
			
			JTextArea ta2 = new JTextArea();
	        
	        try {	        	
	        	BufferedReader br = new BufferedReader(new FileReader(filename), sz);
	        	String line = "";
	        	StringBuilder contents = new StringBuilder();
	        	int cnt = 0;
	        	
	        	while((line = br.readLine()) != null) {
	        		if(cnt + line.length()<=sz) {
	        			contents.append(line);
	        		}
	        		else {
	        			int remain = sz-cnt;
	        			String s = line.substring(0, remain);
	        			contents.append(s);
	        			break;
	        		}
        			contents.append("\n");
        			cnt += line.length();
	        	}
	        	br.close();
	        	ta2.setText(contents.toString());
	        }
	        catch(IOException e3) {
	        	JOptionPane.showMessageDialog(null, "Error", "Alert", JOptionPane.ERROR_MESSAGE);
	        	writeframe.dispose();
	        	throw new IllegalArgumentException("No file exist!");
	        }
	        // modify disable
	        ta2.setEditable(false);
	        JScrollPane ta2Scroll = new JScrollPane(ta2);
	        c3.add(ta2Scroll, BorderLayout.CENTER);

	        JPanel button2 = new JPanel();
	        button2.setLayout(new FlowLayout(FlowLayout.CENTER));
	        JButton check = new JButton("OK");
	        check.addActionListener(new ActionListener(){
	        	public void actionPerformed(ActionEvent e) {
	        		JOptionPane.showMessageDialog(null, "Close", "Message", JOptionPane.INFORMATION_MESSAGE);
	    			writeframe.dispose();
	        	}
	        });

	        button2.add(check);
	        c3.add(button2, BorderLayout.SOUTH);
	        
	        writeframe.setSize(400, 300);
	        writeframe.setVisible(true);
		}
	}
    public static void main(String[] args) {
    	new final_project_main();
    }
}
