package com.pdffocus.reader
import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.*

/** Distinct local recognizer. Line crops are supplied, so layout detection is NOT independent. */
class PaddleLineRecognizer(context:Context):Closeable {
 data class Reading(val text:String,val confidence:Float,val source:Box,val engine:String="PaddleOCR PP-OCRv5 Latin mobile/ORT1.23.2")
 private val env=OrtEnvironment.getEnvironment()
 private val session:OrtSession
 private val dictionary:List<String>
 init {
  val options=OrtSession.SessionOptions().apply{setIntraOpNumThreads(1);setInterOpNumThreads(1);setMemoryPatternOptimization(false);setCPUArenaAllocator(false)}
  session=options.use{context.assets.open("paddle/latin-ppocrv5-mobile.onnx").use{env.createSession(it.readBytes(),options)}}
  val characters=session.metadata.customMetadata["character"]?:error("Missing model character dictionary")
  dictionary=listOf("")+characters.trimEnd('\n','\r').split('\n').map{it.trimEnd('\r')}+listOf(" ")
  check(dictionary.size==504){"Unexpected character dictionary"}
 }
 fun recognize(bitmap:Bitmap,source:Box,obsolete:()->Boolean={false}):Reading? {
  if(obsolete())return null
  val height=48;val width=max(320,ceil(height*bitmap.width.toFloat()/bitmap.height).toInt()).coerceAtMost(1600)
  val resizedWidth=min(width,ceil(height*bitmap.width.toFloat()/bitmap.height).toInt()).coerceAtLeast(1)
  val scaled=Bitmap.createScaledBitmap(bitmap,resizedWidth,height,true)
  val plane=width*height;val data=FloatArray(plane*3);val pixels=IntArray(resizedWidth*height)
  try{scaled.getPixels(pixels,0,resizedWidth,0,0,resizedWidth,height)}finally{if(scaled!==bitmap)scaled.recycle()}
  // Paddle/RapidOCR reference uses OpenCV BGR, channel-first, normalized to [-1,1], zero padding.
  for(y in 0 until height)for(x in 0 until resizedWidth){val color=pixels[y*resizedWidth+x];val p=y*width+x;data[p]=(color and 255)/127.5f-1;data[plane+p]=((color shr 8)and 255)/127.5f-1;data[plane*2+p]=((color shr 16)and 255)/127.5f-1}
  if(obsolete())return null
  OnnxTensor.createTensor(env,FloatBuffer.wrap(data),longArrayOf(1,3,height.toLong(),width.toLong())).use{input->
   session.run(mapOf(session.inputNames.single() to input)).use{result->
    val output=result[0]as OnnxTensor;val shape=output.info.shape;check(shape.size==3&&shape[0]==1L&&shape[2]==dictionary.size.toLong())
    val scores=output.floatBuffer;val out=StringBuilder();var last=-1;var sum=0f;var count=0
    for(t in 0 until shape[1].toInt()){var best=0;var confidence=Float.NEGATIVE_INFINITY;for(c in dictionary.indices){val value=scores.get();if(value>confidence){confidence=value;best=c}}
     if(best!=0&&best!=last){out.append(dictionary[best]);sum+=confidence;count++};last=best
    }
    if(obsolete())return null
    return Reading(out.toString(),if(count==0)0f else sum/count,source)
   }
  }
 }
 override fun close(){session.close()}
}
