package splunk;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;

import com.rs.rsplat.util.MapReduceUtilties;
import com.rs.rsplat.util.Utilities;

@SuppressWarnings("unused")
public class WafFormatMapReduce {
	public static void main(String[] args)
			throws IOException, InterruptedException, ClassNotFoundException, ParseException {
		Configuration conf = new Configuration();
		FileSystem fs = FileSystem.get(conf);
		@SuppressWarnings("deprecation")
		Job job = new Job(conf, "WafMapReduce");

		String inPath = "";
		inPath = "/user/zqz/projIPv6/data/";
		if (fs.exists(new Path(inPath))) {
			FileInputFormat.addInputPath(job, new Path(inPath));
		}

		String outPath = "/user/zqz/projIPv6/result/";
		job.setJarByClass(WafFormatMapReduce.class);
		job.setMapOutputKeyClass(OutputKey.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setMapperClass(FMapper.class);
		job.setReducerClass(FReducer.class);
		job.setGroupingComparatorClass(OutputKeyGroupingComparator.class);
		fs.delete(new Path(outPath), true);
		FileOutputFormat.setOutputPath(job, new Path(outPath));
		job.setNumReduceTasks(1);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}

	public static class FMapper extends Mapper<LongWritable, Text, OutputKey, Text> {
		@Override
		protected void setup(Context context) throws IOException, InterruptedException {

		}

		/**
		 * 进行分段，
		 */
		@Override
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			OutputKey key1 = new OutputKey();
			Text value1 = new Text();

			String[] cur = value.toString().trim().split("\\s+");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < cur.length; i++) {
				if (cur[i].equals("202.114.18.222")) {
					sb.append(cur[i] + "|");
					key1.setKey("Waf");
					key1.setOrder(0l);
					value1.set(sb.toString());
					context.write(key1, value1);
					sb.replace(0, sb.length(), "");
				} else {
					sb.append(cur[i] + "|");
				}
			}
		}
	}

	public static class FReducer extends Reducer<OutputKey, Text, Text, Text> {

		private MultipleOutputs<Text, Text> mos;
		Text key3 = new Text();
		Text value3 = new Text();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			mos = new MultipleOutputs<Text, Text>(context);
		}

		@Override
		protected void cleanup(Context context) throws IOException, InterruptedException {
			mos.close();
		}

		@Override
		public void reduce(OutputKey key, Iterable<Text> value, Context context)
				throws IOException, InterruptedException {
			String strKey = key.getKey();
			for (Text val : value) {
				key3.set(key.getKey());
				value3.set(val);
				mos.write(key3, value3, "s");
			}
		}
	}

	/**
	 * 二次排序用的
	 * 
	 * @author ron
	 * 
	 */
	private static class OutputKey implements WritableComparable<OutputKey> {

		private String key = "";
		private Long order = 0l;

		public OutputKey() {
		}

		public OutputKey(String key) {
			this.key = key;
			this.order = 0l;
		}

		public OutputKey(String key, Long order) {
			this.key = key;
			this.order = order;
		}

		public String getKey() {
			return this.key;
		}

		public long getOrder() {
			return this.order;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public void setOrder(Long order) {
			this.order = order;
		}

		@Override
		public void readFields(DataInput in) throws IOException {
			this.key = in.readUTF();
			this.order = in.readLong();
		}

		@Override
		public void write(DataOutput out) throws IOException {
			out.writeUTF(key);
			out.writeLong(this.order);
		}

		@Override
		public int compareTo(OutputKey other) {
			int compare = this.key.compareTo(other.key);
			if (compare != 0) {
				return compare;
			} else if (this.order != other.order) {
				return order < other.order ? -1 : 1;
			} else {
				return 0;
			}
		}

		@Override
		public String toString() {
			return ToStringBuilder.reflectionToString(this);
		}

		static { // register this comparator
			WritableComparator.define(OutputKey.class, new OutputKeyComparator());
		}
	}

	// key的比较函数
	private static class OutputKeyComparator extends WritableComparator {
		protected OutputKeyComparator() {
			super(OutputKey.class, true);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public int compare(WritableComparable w1, WritableComparable w2) {

			OutputKey p1 = (OutputKey) w1;
			OutputKey p2 = (OutputKey) w2;

			int cmp = p1.getKey().compareTo(p2.getKey());
			if (cmp != 0) {
				return cmp;
			}
			return p1.getOrder() == p2.getOrder() ? 0 : (p1.getOrder() < p2.getOrder() ? -1 : 1);
		}
	}

	// reduce阶段的分组函数
	private static class OutputKeyGroupingComparator extends WritableComparator {

		protected OutputKeyGroupingComparator() {
			super(OutputKey.class, true);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public int compare(WritableComparable o1, WritableComparable o2) {

			OutputKey p1 = (OutputKey) o1;
			OutputKey p2 = (OutputKey) o2;

			return p1.getKey().compareTo(p2.getKey());
		}
	}
}
