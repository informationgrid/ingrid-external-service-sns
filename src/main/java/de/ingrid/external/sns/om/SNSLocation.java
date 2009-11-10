package de.ingrid.external.sns.om;

import de.ingrid.external.om.Location;

public class SNSLocation implements Location {

	private String topicId;
	private String typeId;	 // e.g. use2Type, naturalParkType, ... 
	private String typeName; // e.g. "Federal State", "Nature Park", ... 
	private String name;
	private String qualifier;
	private String nativeKey;
	
	// The coordinates are stored as:
	// 		lower left corner longitude, lower left corner latitude, 
	// 		upper right corner longitude, upper right corner latitude 
	public float[] boundingBox;

	public String getId() {
		return topicId;
	}
	public void setId(String id) {
		this.topicId = id;
	}
	public String getTypeId() {
		return typeId;
	}
	public void setTypeId(String typeId) {
		this.typeId = typeId;
	}
	public String getTypeName() {
		return typeName;
	}
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getQualifier() {
		return qualifier;
	}
	public void setQualifier(String qualifier) {
		this.qualifier = qualifier;
	}
	public float[] getBoundingBox() {
		return boundingBox;
	}
	public void setBoundingBox(float[] boundingBox) {
		this.boundingBox = boundingBox;
	}
	public void setBoundingBox(float bottomLeftLong, float bottomLeftLat, float upperRightLong, float upperRightLat) {
		this.boundingBox = new float[4];
		this.boundingBox[0] = bottomLeftLong;
		this.boundingBox[1] = bottomLeftLat;
		this.boundingBox[2] = upperRightLong;
		this.boundingBox[3] = upperRightLat;
	}
	public String getNativeKey() {
		return nativeKey;
	}
	public void setNativeKey(String nativeKey) {
		this.nativeKey = nativeKey;
	}

	public String toString() {
		String result = "[";
		result += "ID: "+this.topicId;
		result += ", Name: "+this.name;
		result += ", Type ID: "+this.typeId;
		result += ", Type Name: "+this.typeName;
		result += ", Qualifier: "+this.qualifier;
		result += ", Native Key: "+this.nativeKey;
		if (this.boundingBox != null && this.boundingBox.length == 4) {
			result += ", WGS84Box: "+this.boundingBox[0]+","+this.boundingBox[1]+" "+this.boundingBox[2]+","+this.boundingBox[3];
		}
		result += "]";
		return result;
	}
}
