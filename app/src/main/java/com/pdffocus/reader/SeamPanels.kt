package com.pdffocus.reader
import kotlin.math.*

/** Frameless comic panels found by recursive minimum-cost seam cuts on the paper-distance
 * ink mask. A seam follows thin or slanted gutters and detours around bubbles crossing a
 * gutter; each side owns its pixels exactly, so protruding bubbles are never sliced by the
 * rectangular output boxes. Enclosed paper islands (bubble interiors) are impassable for a
 * seam, a seam may not detach a readable fragment of a connected stroke (balloon tail,
 * letter), and a seam may not run through an unbordered caption: when no clean cut exists
 * the panels stay merged (or detection is refused) rather than cutting content.
 * Used only after the closed black frames of [ComicPanels] are absent, and only in Comic and
 * Manga modes. Known limits: panels merged by overlapping artwork or balloons stay merged. */
object SeamPanels {
    var trace:((String)->Unit)?=null

    data class Params(
        val targetWidth:Int=900,
        val clearDist:Float=1.6f,     // px of clearance that counts as "in a gutter"
        val inkCost:Float=1000f,
        val bendCost:Float=0.20f,
        val maxInkFraction:Float=0.01f, // isolated specks only: never a drawn stroke (>=3px)
        val maxInkRun:Int=2,
        val maxSplitFragment:Int=8, // px of a connected stroke a seam may detach (junction slivers stay on the art they were drawn over)
        val minClearFraction:Float=0.55f,
        val maxDevRms:Float=3.0f,
        val maxDev90:Int=14,
        val minRegionFrac:Float=0.05f,
        val minPanelArea:Float=0.015f,
        val maxPanels:Int=20
    )

    fun find(pixels:IntArray,width:Int,height:Int,P:Params=Params(),cancelled:()->Boolean={false}):List<Box>{
        require(pixels.size==width*height)
        if(width<80||height<80)return emptyList()
        val scale=min(1f,P.targetWidth.toFloat()/width)
        val w=max(80,(width*scale).roundToInt());val h=max(80,(height*scale).roundToInt())
        val r=IntArray(w*h);val g=IntArray(w*h);val b=IntArray(w*h)
        for(y in 0 until h){val sy=min(height-1,(y.toLong()*height/h).toInt())
            for(x in 0 until w){val p=pixels[sy*width+min(width-1,(x.toLong()*width/w).toInt())]
                r[y*w+x]=(p ushr 16)and 255;g[y*w+x]=(p ushr 8)and 255;b[y*w+x]=p and 255}}
        val band=max(1,(min(h,w)*.02f).roundToInt())
        val buckets=HashMap<Int,MutableList<Int>>()
        for(y in 0 until h)for(x in 0 until w)if(y<band||y>=h-band||x<band||x>=w-band){
            val i=y*w+x;buckets.getOrPut((r[i]/16)*256+(g[i]/16)*16+b[i]/16){mutableListOf()}+=i}
        val mode=buckets.values.maxByOrNull{it.size}?:return emptyList()
        fun med(c:IntArray)=mode.map{c[it]}.sorted()[mode.size/2]
        val pr=med(r);val pg=med(g);val pb=med(b)
        val ink=BooleanArray(w*h){abs(r[it]-pr)+abs(g[it]-pg)+abs(b[it]-pb)>60}
        if(cancelled())return emptyList()
        val CAP=24f
        val dist=FloatArray(w*h){if(ink[it])0f else CAP}
        for(y in 0 until h)for(x in 0 until w){val i=y*w+x;var d=dist[i]
            if(x>0)d=min(d,dist[i-1]+1f);if(y>0){d=min(d,dist[i-w]+1f);if(x>0)d=min(d,dist[i-w-1]+1.4f);if(x<w-1)d=min(d,dist[i-w+1]+1.4f)}
            dist[i]=d}
        for(y in h-1 downTo 0)for(x in w-1 downTo 0){val i=y*w+x;var d=dist[i]
            if(x<w-1)d=min(d,dist[i+1]+1f);if(y<h-1){d=min(d,dist[i+w]+1f);if(x<w-1)d=min(d,dist[i+w+1]+1.4f);if(x>0)d=min(d,dist[i+w-1]+1.4f)}
            dist[i]=d}
        if(cancelled())return emptyList()
        // Enclosed paper islands: bubble interiors are paper-coloured regions fully surrounded
        // by ink. A seam must never pass through one (its white interior looks like a gutter
        // to the distance cost, so without this a bubble straddling two panels gets sliced).
        // Reachability uses 1px-dilated ink to close anti-aliased border gaps; thin pinched
        // gutter segments are rejected by the blob shape filters below.
        val island=BooleanArray(w*h)
        val sandw=BooleanArray(w*h)
        // Open text guard: a paper pixel sandwiched between close SHORT ink runs above AND
        // below (letters, not panel edges) sits between the lines of an unbordered caption.
        // Panel borders are long runs and never trigger this. A seam must not split a caption.
        val rowRun=IntArray(w*h)
        for(y in 0 until h){var x=0
            while(x<w){if(!ink[y*w+x]){x++;continue}
                var x2=x;while(x2<w&&ink[y*w+x2])x2++
                for(xx in x until x2)rowRun[y*w+xx]=x2-x;x=x2}}
        val colRun=IntArray(w*h)
        for(x in 0 until w){var y=0
            while(y<h){if(!ink[y*w+x]){y++;continue}
                var y2=y;while(y2<h&&ink[y2*w+x])y2++
                for(yy in y until y2)colRun[yy*w+x]=y2-y;y=y2}}
        for(x in 0 until w){
            val upD=IntArray(h);var li=-1000
            for(y in 0 until h){if(ink[y*w+x])li=y else upD[y]=y-li}
            li=100000
            for(y in h-1 downTo 0){if(ink[y*w+x])li=y else{val dn=li-y;val up=upD[y]
                if(up>0&&dn>0&&up+dn<=44
                    &&rowRun[(y-up)*w+x]<=80&&rowRun[(y+dn)*w+x]<=80)sandw[y*w+x]=true}}
        }
        run{
            val inkD=BooleanArray(w*h)
            for(y in 0 until h)for(x in 0 until w){val i=y*w+x
                if(ink[i]){inkD[i]=true
                    if(x>0)inkD[i-1]=true;if(x<w-1)inkD[i+1]=true
                    if(y>0)inkD[i-w]=true;if(y<h-1)inkD[i+w]=true}}
            val reach=BooleanArray(w*h);val q=IntArray(w*h);var qh=0;var qt=0
            fun push(i:Int){if(!inkD[i]&&!reach[i]){reach[i]=true;q[qt++]=i}}
            for(x in 0 until w){push(x);push((h-1)*w+x)}
            for(y in 0 until h){push(y*w);push(y*w+w-1)}
            while(qh<qt){val p=q[qh++];val x=p%w;val y=p/w
                if(x>0)push(p-1);if(x<w-1)push(p+1);if(y>0)push(p-w);if(y<h-1)push(p+w)}
            val seen=BooleanArray(w*h)
            val minBlob=48
            for(s in 0 until w*h){
                if(inkD[s]||reach[s]||seen[s])continue
                qh=0;qt=0;seen[s]=true;q[qt++]=s
                var size=0;var ct2=h;var cl2=w;var cb2=0;var cr2=0
                while(qh<qt){val p=q[qh++];size++;val x=p%w;val y=p/w
                    if(y<ct2)ct2=y;if(y>cb2)cb2=y;if(x<cl2)cl2=x;if(x>cr2)cr2=x
                    for(d in intArrayOf(-1,1,-w,w)){val pp=p+d
                        val xx=pp%w;val ok=if(d==-1)x>0 else if(d==1)x<w-1 else pp in 0 until w*h
                        if(ok&&pp in 0 until w*h&&!inkD[pp]&&!reach[pp]&&!seen[pp]){seen[pp]=true;q[qt++]=pp}}}
                val bw=cr2-cl2+1;val bh=cb2-ct2+1
                // blob-shaped and bubble-sized only: an enclosed gutter stripe (framed panels
                // touching the content bounds) is a corridor for seams, never a wall
                if(size<minBlob||min(bw,bh)<6||size<bw.toLong()*bh/4)continue
                if(bw>w*.35f||bh>h*.35f||size>w.toLong()*h/50)continue
                for(k in 0 until qt)island[q[k]]=true}
        }
        // Stroke pixels: ink within ~2px of paper (borders, tails, letters). Solid art
        // interiors are excluded, so a seam clipping a panel edge does not count art as a
        // detached fragment.
        val inkDist=FloatArray(w*h){if(ink[it])CAP else 0f}
        for(y in 0 until h)for(x in 0 until w){val i=y*w+x;if(!ink[i])continue
            var d=inkDist[i]
            if(x>0)d=min(d,inkDist[i-1]+1f);if(y>0){d=min(d,inkDist[i-w]+1f);if(x>0)d=min(d,inkDist[i-w-1]+1.4f);if(x<w-1)d=min(d,inkDist[i-w+1]+1.4f)}
            inkDist[i]=d}
        for(y in h-1 downTo 0)for(x in w-1 downTo 0){val i=y*w+x;if(!ink[i])continue
            var d=inkDist[i]
            if(x<w-1)d=min(d,inkDist[i+1]+1f);if(y<h-1){d=min(d,inkDist[i+w]+1f);if(x<w-1)d=min(d,inkDist[i+w+1]+1.4f);if(x>0)d=min(d,inkDist[i+w-1]+1.4f)}
            inkDist[i]=d}
        val strokePx=BooleanArray(w*h){ink[it]&&inkDist[it]<=2.5f}
        // Connected ink strokes. A good seam crosses nothing; a seam that would detach more
        // than maxSplitFragment px of a stroke from the rest of it (a balloon tail, a letter)
        // is rejected, the stroke becomes impassable, and the seam is recomputed. Junction
        // slivers stay readable: they land on the art they were drawn over.
        val stroke=IntArray(w*h){-1}
        val strokePix=ArrayList<IntArray>()
        run{
            val q=IntArray(w*h)
            for(s0 in 0 until w*h){
                if(!ink[s0]||stroke[s0]>=0)continue
                var qh=0;var qt=0;val sid=strokePix.size;stroke[s0]=sid;q[qt++]=s0
                val tmp=ArrayList<Int>();tmp+=s0
                while(qh<qt){val p0=q[qh++];val x=p0%w
                    for(d in intArrayOf(-1,1,-w,w)){val pp=p0+d
                        val ok=if(d==-1)x>0 else if(d==1)x<w-1 else pp in 0 until w*h
                        if(ok&&pp in 0 until w*h&&ink[pp]&&stroke[pp]<0){stroke[pp]=sid;q[qt++]=pp;tmp+=pp}}}
                strokePix+=tmp.toIntArray()}
        }
        trace?.invoke("sandwPx="+sandw.count{it})
        val cost=FloatArray(w*h){if(island[it])1e17f else{val d=dist[it];(if(d<=0f)P.inkCost else P.clearDist/min(d,P.clearDist))+if(sandw[it])50f else 0f}}
        val pageArea=w.toLong()*h
        val owner=IntArray(w*h) // region id; 0 = whole content
        var nextId=1
        val out=ArrayList<Int>() // region ids emitted
        class Seam(val axis:Int,val pts:IntArray,val score:Float)
        fun bestSeam(id:Int,t:Int,l:Int,b:Int,r:Int,axis:Int,minSide:Int):Seam?{
            val steps=if(axis==0)r-l else b-t
            val sweep=if(axis==0)b-t else r-l
            if(steps<8||sweep<2*minSide+8)return null
            val lo=minSide;val hi=sweep-1-minSide
            val INF=1e18f
            var dp=FloatArray(sweep){INF}
            var nd=FloatArray(sweep){INF} // swapped with dp each step: no per-step allocation
            val par=IntArray(steps*sweep){-1}
            fun cell(pos:Int,step:Int)=if(axis==0)cost[(t+pos)*w+(l+step)] else cost[(t+step)*w+(l+pos)]
            for(pos in lo..hi)dp[pos]=cell(pos,0)
            for(step in 1 until steps){
                java.util.Arrays.fill(nd,INF)
                for(pos in lo..hi){
                    var best=INF;var bp=-1
                    for(dpos in -1..1){val pp=pos+dpos;if(pp<lo||pp>hi)continue
                        val v=dp[pp]+(if(dpos==0)0f else P.bendCost)
                        if(v<best){best=v;bp=pp}}
                    if(bp<0)continue
                    val c=cell(pos,step);if(c>=INF/2)continue
                    nd[pos]=best+c;par[step*sweep+pos]=bp}
                val tmp=dp;dp=nd;nd=tmp}
            var endPos=-1;var bestTot=INF
            for(pos in lo..hi)if(dp[pos]<bestTot){bestTot=dp[pos];endPos=pos}
            if(endPos<0||bestTot>=INF/2)return null
            val pts=IntArray(steps);var p=endPos
            for(step in steps-1 downTo 0){pts[step]=p;p=par[step*sweep+p]}
            var inkN=0;var clearN=0;var bends=0;var inkRun=0;var inkRunMax=0;var sandwN=0;var sandwRun=0;var sandwRunMax=0
            val dev=IntArray(steps)
            // least-squares line fit: a steadily slanted gutter fits, a winding dodge-path does not
            val nF=steps.toFloat();val sx=nF*(steps-1)/2;val sxx=nF*(steps-1)*(2*steps-1)/6
            var sy=0f;var sxy=0f;for(i in 0 until steps){sy+=pts[i];sxy+=pts[i]*i}
            val denom=nF*sxx-sx*sx
            val slope=if(abs(denom)<1e-6f)0f else (nF*sxy-sx*sy)/denom
            val intercept=(sy-slope*sx)/nF
            var devSum=0.0
            for(step in 0 until steps){
                val d=if(axis==0)dist[(t+pts[step])*w+(l+step)] else dist[(t+step)*w+(l+pts[step])]
                if(d<=0f){inkN++;inkRun++;inkRunMax=max(inkRunMax,inkRun)}else inkRun=0
                if(sandw[if(axis==0)(t+pts[step])*w+(l+step) else (t+step)*w+(l+pts[step])]){sandwN++;sandwRun++;sandwRunMax=max(sandwRunMax,sandwRun)}else sandwRun=0
                if(d>=P.clearDist)clearN++
                if(step>0&&pts[step]!=pts[step-1])bends++
                val expected=intercept+slope*step
                val dv=abs(pts[step]-expected);dev[step]=dv.roundToInt();devSum+=dv*dv}
            val dev90=dev.sorted()[(steps*0.9f).toInt().coerceAtMost(steps-1)]
            val inkFrac=inkN.toFloat()/steps;val clearFrac=clearN.toFloat()/steps
            val rms=sqrt(devSum/steps).toFloat()
            trace?.invoke("  try axis=$axis region=[$t,$l,$b,$r] ink=${"%.3f".format(inkFrac)} clear=${"%.2f".format(clearFrac)} rms=${"%.1f".format(rms)} dev90=$dev90 inkRun=$inkRunMax sandwRun=$sandwRunMax")
            if(inkFrac>P.maxInkFraction||inkRunMax>P.maxInkRun)return null
            // A seam must not split an open caption: refuse a long solid sandwiched stretch,
            // or a path sandwiched over a third of its length (measured: lettering ~0.34,
            // real gutters with bubbles/jagged art stay under ~0.27).
            if(sandwRunMax>80||sandwN>steps*.30f)return null
            if(clearFrac<P.minClearFraction)return null
            if(rms>P.maxDevRms||dev90>P.maxDev90)return null
            return Seam(axis,pts,clearFrac-2f*inkFrac-0.002f*bends)
        }
        fun split(id:Int,t0:Int,l0:Int,b0:Int,r0:Int,depth:Int){
            if(cancelled())return
            // Re-trim pure-paper borders: margin strips beside a shorter neighbour must never
            // become fake seams that hide the real interior gutter from the search.
            var t=t0;var l=l0;var b=b0;var r=r0
            fun rowInkR(y:Int):Boolean{var n=0;for(x in l until r)if(ink[y*w+x])n++;return n>(r-l)*.002f}
            fun colInkR(x:Int):Boolean{var n=0;for(y in t until b)if(ink[y*w+x])n++;return n>(b-t)*.002f}
            while(t<b-1&&!rowInkR(t))t++
            while(b>t+1&&!rowInkR(b-1))b--
            while(l<r-1&&!colInkR(l))l++
            while(r>l+1&&!colInkR(r-1))r--
            if(out.size>=P.maxPanels){out+=id;return}
            if((r-l).toLong()*(b-t)<pageArea*P.minPanelArea*1.5f||depth>14){out+=id;return}
            var inkN=0;for(y in t until b){for(x in l until r)if(ink[y*w+x]&&owner[y*w+x]==id)inkN++}
            if(inkN.toDouble()/((r-l).toLong()*(b-t))<0.002)return // pure-paper remainder
            val minSweepY=(h*P.minRegionFrac).roundToInt();val minSweepX=(w*P.minRegionFrac).roundToInt()
            var s:Seam?=null
            var tries=0
            while(tries<4){
                tries++
                var best:Seam?=null
                for(axis in 0..1){
                    val minSide=if(axis==0)minSweepY else minSweepX
                    val c=bestSeam(id,t,l,b,r,axis,minSide)?:continue
                    if(best==null||c.score>best.score)best=c
                }
                val cand=best?:break
                // Split-fragment guard: refuse a seam that detaches a readable fragment of a
                // connected stroke from the rest of it; wall the stroke and search again.
                val crossed=HashSet<Int>()
                val crossSteps=ArrayList<Int>()
                for(step in 0 until cand.pts.size){
                    val yy=if(cand.axis==0)t+cand.pts[step] else t+step
                    val xx=if(cand.axis==0)l+step else l+cand.pts[step]
                    if(dist[yy*w+xx]<=0f){crossSteps+=step
                        val lb=stroke[yy*w+xx];if(lb>=0)crossed+=lb}}
                var walled=false
                for(lb in crossed){
                    // Detached fragment = STROKE pixels of the crossed component on the
                    // minority side, near (+-60 steps along the seam) a crossing. Count +
                    // perpendicular extent: a clipped art edge is wide but 2px thin and
                    // stays readable on the art it was drawn over; a severed balloon tail
                    // or letter reaches far from the cut.
                    var sideA=0;var sideB=0
                    var aMin=Int.MAX_VALUE;var aMax=Int.MIN_VALUE;var bMin=Int.MAX_VALUE;var bMax=Int.MIN_VALUE
                    for(pp in strokePix[lb]){if(!strokePx[pp])continue
                        val x=pp%w;val y=pp/w
                        val sp=if(cand.axis==0)x-l else y-t
                        if(sp<0||sp>=cand.pts.size)continue
                        if(crossSteps.none{abs(it-sp)<=60})continue
                        val cut=if(cand.axis==0)t+cand.pts[sp] else l+cand.pts[sp]
                        val perp=if(cand.axis==0)y else x
                        if(if(cand.axis==0)y<=cut else x<=cut){sideA++;if(perp<aMin)aMin=perp;if(perp>aMax)aMax=perp}
                        else{sideB++;if(perp<bMin)bMin=perp;if(perp>bMax)bMax=perp}}
                    val minor=minOf(sideA,sideB)
                    val ext=if(sideA<=sideB)(if(sideA>0)aMax-aMin+1 else 0) else (if(sideB>0)bMax-bMin+1 else 0)
                    if(minor>P.maxSplitFragment&&ext>=48){
                        trace?.invoke("  wall stroke=$lb fragment=$minor extent=$ext")
                        for(pp in strokePix[lb])cost[pp]=1e17f
                        walled=true}}
                if(!walled){s=cand;break}
            }
            if(s==null){out+=id;return}
            val idA=nextId++;val idB=nextId++
            var areaA=0L;var areaB=0L
            var at=h;var al=w;var ab=0;var ar=0;var bt=h;var bl=w;var bb=0;var br=0
            fun give(i:Int,nid:Int,a:Boolean){owner[i]=nid
                val y=i/w;val x=i%w
                if(a){areaA++;if(y<at)at=y;if(y>ab)ab=y;if(x<al)al=x;if(x>ar)ar=x}
                else{areaB++;if(y<bt)bt=y;if(y>bb)bb=y;if(x<bl)bl=x;if(x>br)br=x}}
            if(s.axis==0){ // horizontal seam: above = A, below = B
                for(step in 0 until s.pts.size){val x=l+step;val cut=t+s.pts[step]
                    for(y in t..cut){val i=y*w+x;if(owner[i]==id)give(i,idA,true)}
                    for(y in cut+1 until b){val i=y*w+x;if(owner[i]==id)give(i,idB,false)}}
            }else{
                for(step in 0 until s.pts.size){val y=t+step;val cut=l+s.pts[step]
                    for(x in l..cut){val i=y*w+x;if(owner[i]==id)give(i,idA,true)}
                    for(x in cut+1 until r){val i=y*w+x;if(owner[i]==id)give(i,idB,false)}}}
            // Recursion splits rectangles at the seam median (robust cut discovery);
            // pixel attribution follows the seam itself (slice-free output boxes).
            val sweepLen=if(s.axis==0)b-t else r-l
            val med=s.pts.sorted()[s.pts.size/2].coerceIn(1,sweepLen-2)
            trace?.invoke("cut depth=$depth axis=${s.axis} at=$med region=[$t,$l,$b,$r] areas=$areaA/$areaB score=${"%.2f".format(s.score)}")
            // Side-size guard: deliberately below the output filter — paper re-trim already
            // removed margin strips, and owned-pixel counts underestimate thin text strips.
            if(areaA<pageArea*P.minPanelArea*.6f||areaB<pageArea*P.minPanelArea*.6f){
                for(y in t until b)for(x in l until r){val i=y*w+x;if(owner[i]==idA||owner[i]==idB)owner[i]=id}
                nextId-=2
                out+=id;return}
            if(s.axis==0){split(idA,t,l,t+med,r,depth+1);split(idB,t+med+1,l,b,r,depth+1)}
            else{split(idA,t,l,b,l+med,depth+1);split(idB,t,l+med+1,b,r,depth+1)}
        }
        // content bounds: trim uniform paper margins
        var ct=0;var cl=0;var cb=h;var cr=w
        fun rowInk(y:Int):Boolean{var n=0;for(x in 0 until w)if(ink[y*w+x])n++;return n>w*.002f}
        fun colInk(x:Int):Boolean{var n=0;for(y in 0 until h)if(ink[y*w+x])n++;return n>h*.002f}
        while(ct<h-1&&!rowInk(ct))ct++
        while(cb>ct+1&&!rowInk(cb-1))cb--
        while(cl<w-1&&!colInk(cl))cl++
        while(cr>cl+1&&!colInk(cr-1))cr--
        // A ruled grid (forms, tables) is not frameless comic art. Framed comics are handled
        // by ComicPanels before this runs, so long straight rules here mean: do not cut.
        var ruledRows=0;var ruledCols=0;var runR=0;var runC=0
        for(y in ct until cb){var best=0;var cur=0;for(x in cl until cr){if(ink[y*w+x]){cur++;if(cur>best)best=cur}else cur=0}
            if(best>(cr-cl)*.85f){runR++;if(runR>=2)ruledRows++}else runR=0}
        for(x in cl until cr){var best=0;var cur=0;for(y in ct until cb){if(ink[y*w+x]){cur++;if(cur>best)best=cur}else cur=0}
            if(best>(cb-ct)*.85f){runC++;if(runC>=2)ruledCols++}else runC=0}
        if(ruledRows>=4&&ruledCols>=2)return emptyList()
        split(0,ct,cl,cb,cr,0)
        if(cancelled())return emptyList()
        // ink bounding boxes of owned regions
        data class R(var t:Int,var l:Int,var b:Int,var r:Int,var ink:Int)
        val regs=HashMap<Int,R>()
        val emitted=out.toHashSet()
        for(y in ct until cb)for(x in cl until cr){val i=y*w+x;if(!ink[i])continue
            val id=owner[i];if(id !in emitted)continue
            val reg=regs.getOrPut(id){R(y,x,y,x,0)}
            if(y<reg.t)reg.t=y;if(y>reg.b)reg.b=y;if(x<reg.l)reg.l=x;if(x>reg.r)reg.r=x;reg.ink++}
        val rs=regs.filter{(_,v)->(v.b-v.t+1).toLong()*(v.r-v.l+1)>=pageArea*P.minPanelArea}.values.toMutableList()
        // resolve residual bbox overlaps: cut where it crosses the least owned ink of both sides
        var changed=true
        while(changed){changed=false
            outer@ for(i in rs.indices)for(j in i+1 until rs.size){
                val A=rs[i];val B=rs[j]
                // +1 normalisation on right/bottom edges makes touching pixels overlap in float
                val ol=min(A.r,B.r)-max(A.l,B.l);val ot=min(A.b,B.b)-max(A.t,B.t)
                if(ol<0||ot<0)continue
                // separate along the axis with the larger centre distance
                val cxA=(A.l+A.r)/2f;val cxB=(B.l+B.r)/2f;val cyA=(A.t+A.b)/2f;val cyB=(B.t+B.b)/2f
                if(abs(cxA-cxB)>=abs(cyA-cyB)){
                    val left=if(cxA<cxB)A else B;val right=if(cxA<cxB)B else A
                    var bestC=(left.r+right.l)/2;var bestInk=Long.MAX_VALUE
                    for(c in right.l..left.r){var cnt=0L;var blob=false
                        for(y in max(left.t,right.t)..min(left.b,right.b)){val i=y*w+c;if(island[i]){blob=true;break};if(ink[i])cnt++}
                        if(blob)continue
                        if(cnt<bestInk){bestInk=cnt;bestC=c}}
                    if(bestInk==Long.MAX_VALUE){merge@ run{A.t=min(A.t,B.t);A.b=max(A.b,B.b);A.l=min(A.l,B.l);A.r=max(A.r,B.r);A.ink+=B.ink};rs.removeAt(j);changed=true;break@outer}
                    left.r=bestC;right.l=bestC+1;changed=true
                }else{
                    val top=if(cyA<cyB)A else B;val bot=if(cyA<cyB)B else A
                    var bestC=(top.b+bot.t)/2;var bestInk=Long.MAX_VALUE
                    for(c in bot.t..top.b){var cnt=0L;var blob=false
                        for(x in max(top.l,bot.l)..min(top.r,bot.r)){val i=c*w+x;if(island[i]){blob=true;break};if(ink[i])cnt++}
                        if(blob)continue
                        if(cnt<bestInk){bestInk=cnt;bestC=c}}
                    if(bestInk==Long.MAX_VALUE){A.t=min(A.t,B.t);A.b=max(A.b,B.b);A.l=min(A.l,B.l);A.r=max(A.r,B.r);A.ink+=B.ink;rs.removeAt(j);changed=true;break@outer}
                    top.b=bestC;bot.t=bestC+1;changed=true
                }
            }
        }
        // Inset each box by ~1.5px: touching boxes leave no gap for ComicPagePartition to cut.
        val ix=1.5f/w;val iy=1.5f/h
        val boxes=rs.map{Box(it.l.toFloat()/w+ix,it.t.toFloat()/h+iy,(it.r+1).toFloat()/w-ix,(it.b+1).toFloat()/h-iy)}
            .filter{it.area>=P.minPanelArea}
        val area=boxes.sumOf{it.area.toDouble()}
        if(boxes.size<2||area<.2)return emptyList()
        // Ruled text is not a comic: most panels must hold illustration, i.e. interior ink
        // with a substantial vertical mark, not only short horizontal text strokes.
        val illustrated=boxes.count{bx->
            val l=((bx.left)*w).toInt().coerceIn(0,w-1);val r=((bx.right)*w).toInt().coerceIn(l+1,w)
            val t=((bx.top)*h).toInt().coerceIn(0,h-1);val b2=((bx.bottom)*h).toInt().coerceIn(t+1,h)
            var inkN=0;var samples=0;var longest=0
            for(x in l until r step 2){var run=0;for(y in t until b2){if(ink[y*w+x]){inkN++;run++;longest=max(longest,run)}else run=0;samples++}}
            samples>0&&inkN.toFloat()/samples>.035f&&longest>max((b2-t)*.12f,24f)}
        if(illustrated<max(2,ceil(boxes.size*.6f).toInt()))return emptyList()
        return boxes.sortedWith(compareBy<Box>{(it.top*35).toInt()}.thenBy{it.left})
    }
}
