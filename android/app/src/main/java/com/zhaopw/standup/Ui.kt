package com.zhaopw.standup

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.*

internal object Palette {
 val brand=Color.rgb(40,124,112); val soft=Color.rgb(232,243,240); val bg=Color.rgb(244,246,248)
 val ink=Color.rgb(24,33,43); val muted=Color.rgb(102,115,130); val warm=Color.rgb(244,238,228)
}
internal class Ui(val context: Context) {
 fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
 fun column()=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
 fun row()=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
 fun text(value:String,size:Float=14f,color:Int=Palette.muted)=TextView(context).apply { text=value; textSize=size; setTextColor(color); setPadding(0,dp(5),0,dp(5)) }
 fun shape(color:Int,radius:Int=22)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
 fun card()=column().apply { setPadding(dp(20),dp(18),dp(20),dp(18)); background=shape(Color.WHITE); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) } }
 fun button(value:String,primary:Boolean=false,tagValue:String?=null,action:()->Unit)=Button(context).apply {
  text=value; textSize=14f; isAllCaps=false; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
  minHeight=dp(48); minimumHeight=dp(48); minimumWidth=dp(48); setPadding(dp(12),dp(4),dp(12),dp(4))
  background=android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22287C70),shape(if(primary) Palette.brand else Color.TRANSPARENT,14),null)
  setTextColor(if(primary) Color.WHITE else Palette.brand); tag=tagValue; setOnClickListener { action() }
 }
 fun icon(resource:Int,description:String,tagValue:String?=null,action:()->Unit)=ImageButton(context).apply {
  setImageResource(resource); imageTintList=ColorStateList.valueOf(Palette.brand); contentDescription=description; tag=tagValue
  background=shape(Palette.soft,14); setPadding(dp(12),dp(12),dp(12),dp(12)); minimumHeight=dp(48); minimumWidth=dp(48)
  layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)); setOnClickListener { action() }
 }
 fun equal(row:LinearLayout,view:View) { row.addView(view,LinearLayout.LayoutParams(0,-2,1f)) }
 fun insets(view:View) { view.setOnApplyWindowInsetsListener { v,i ->
  if(Build.VERSION.SDK_INT>=30) { val s=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()); v.setPadding(s.left,s.top,s.right,s.bottom) }
  else { @Suppress("DEPRECATION") v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom) }; i
 } }
}
