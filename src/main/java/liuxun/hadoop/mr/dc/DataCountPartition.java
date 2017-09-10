package liuxun.hadoop.mr.dc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class DataCountPartition {

	public static class DCMapper extends Mapper<LongWritable, Text, Text, DataBean> {

		@Override
		protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			// accept
			String line = value.toString();
			// split
			String[] fields = line.split("\t");
			String tel = fields[1];
			long up = Long.parseLong(fields[8]);
			long down = Long.parseLong(fields[9]);
			DataBean bean = new DataBean(tel, up, down);
			// send
			context.write(new Text(tel), bean);
		}

	}

	public static class DCReducer extends Reducer<Text, DataBean, Text, DataBean> {

		@Override
		protected void reduce(Text key, Iterable<DataBean> values, Context context)
				throws IOException, InterruptedException {
			long up_sum = 0;
			long down_sum = 0;
			for (DataBean bean : values) {
				up_sum += bean.getUpPayLoad();
				down_sum += bean.getDownPayLoad();
			}
			DataBean bean = new DataBean("", up_sum, down_sum);
			context.write(key, bean);
		}

	}

	public static class ProviderPartitioner extends Partitioner<Text, DataBean> {

		private static Map<String, Integer> prividerMap = new HashMap<String, Integer>();
		static {
			// 实际开发时是从数据库加载这种映射关系的
			// 1：中国移动 2：中国联通 3：中国电信
			prividerMap.put("135", 1);
			prividerMap.put("136", 1);
			prividerMap.put("137", 1);
			prividerMap.put("150", 2);
			prividerMap.put("159", 2);
			prividerMap.put("182", 3);
			prividerMap.put("183", 3);
		}

		// 此方法的返回值是分区号
		// key: mapper一次输出的key 这里是手机号
		// key: mapper一次输出的Value 这里是DataBean
		// numPartitions:分区数量，由Reducer的数量决定，启动几个Reducer就会有几个partition
		@Override
		public int getPartition(Text key, DataBean value, int numPartitions) {
			// 根据手机号得到运营商 此处根据key进行分区，实际开发中也可以根据value进行分区
			String account = key.toString();
			String sub_acc = account.substring(0, 3);
			Integer code = prividerMap.get(sub_acc);
			if (code == null) {
				code  =0;
			}
			return code;
		}

	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf);

		job.setJarByClass(DataCountPartition.class);

		job.setMapperClass(DCMapper.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(DataBean.class);
		FileInputFormat.setInputPaths(job, new Path(args[0]));

		job.setReducerClass(DCReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(DataBean.class);
		FileOutputFormat.setOutputPath(job, new Path(args[1]));

		job.setPartitionerClass(ProviderPartitioner.class);
		
		// 设置启动Reducer的数量
		job.setNumReduceTasks(Integer.parseInt(args[2]));
		
		job.waitForCompletion(true);

	}

}
