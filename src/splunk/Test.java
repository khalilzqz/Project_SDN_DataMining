package splunk;

public class Test {
	public String typeSplitM(String str, String type) {
		String[] cur = str.split(type + "=\"\"");
		String outStr = "";
		if (cur.length > 1) {
			outStr = cur[1].split("\"")[0];
		}
		return outStr;
	}

	public static void main(String[] args) {
		String str = "srcintf=\"\"port28\"\"";
		Test t = new Test();
		System.out.println(t.typeSplitM(str, "srcintf"));

	}

}
