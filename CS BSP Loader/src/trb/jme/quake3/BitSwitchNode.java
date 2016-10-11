package trb.jme.quake3;

import com.jme3.scene.*;

import java.util.BitSet;

/**
 * <code>BSwitchNode</code> defines a node that maintains a set of active children
 */
public class BitSwitchNode extends Node {
	private static final long serialVersionUID = 1L;

	protected BitSet bitSet;

	/**
	 * Constructor instantiates a new <code>BitSwitchNode</code> object. The name
	 * of the node is provided during construction.
	 * 
	 * @param name the name of the node.
	 * @param bitSet contains which children to render. Length must match number of children.
	 */
	public BitSwitchNode(String name, BitSet bitSet) {
		super(name);
		this.bitSet = bitSet;
	}
}