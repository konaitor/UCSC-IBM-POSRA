package javajs.swing;

import javajs.util.List;

public class JContainer extends JComponent {

	protected List<JComponent> list;
	
	public JContainer(String type) {
		super(type);
		list = new List<JComponent>();
	}
	public void removeAll() {
		list.clear();
	}

	public JComponent add(JComponent component) {
		list.addLast(component);
		return component;
	}

	@Override
	public int getSubcomponentWidth() {
		return (list.size() == 1 ? list.get(0).getSubcomponentWidth() : 0);
	}
	
	@Override
	public int getSubcomponentHeight() {
		return (list.size() == 1 ? list.get(0).getSubcomponentHeight() : 0);
	}
	@Override
	public String toHTML() {
		return null;
	}


}
