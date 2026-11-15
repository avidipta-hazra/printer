<?php
/*
Plugin Name: WP Attendance Tracker - LD0801 Native
Description: Attendance labels printed directly to the Android LD0801 Native Bridge.
Version: 4.5
*/

if (!defined('ABSPATH')) exit;

add_shortcode('attendance', 'att_native_shortcode');

function att_native_shortcode() {
    global $wpdb;
    $table = $wpdb->prefix . 'attendance';
    $rows = $wpdb->get_results("SELECT * FROM $table");

    ob_start();
    ?>
    <style>
    #att-search{display:block;width:70%;margin:20px auto;padding:8px 12px;font-size:16px;border:1px solid #000;box-sizing:border-box}
    .att-box{display:flex;flex-direction:row;margin:10px auto;width:70%;min-height:190px;border:1px solid #000;border-radius:6px;background:#fff;overflow:hidden;padding:3px;box-sizing:border-box}
    .att-col{flex:1;height:100%;display:flex;flex-direction:column;justify-content:center;align-items:center;box-sizing:border-box}
    .att-col>div{padding:10px 5px;margin-bottom:15px;width:100%;box-sizing:border-box;text-align:center}
    .att-btn{margin-top:15px;padding:12px 24px;border:0;font-weight:bold;cursor:pointer;font-size:18px;border-radius:4px;white-space:nowrap}
    .att-logo{width:60px;height:60px;object-fit:contain;display:block;margin:auto}
    .att-printing{opacity:.55;pointer-events:none}
    </style>

    <input id="att-search" type="text" placeholder="Search by Name, Mobile, or Reg">

    <div id="att-container">
    <?php foreach ($rows as $row): ?>
        <div class="att-box"
             data-id="<?php echo esc_attr($row->id); ?>"
             data-name="<?php echo esc_attr(strtolower($row->name)); ?>"
             data-mob="<?php echo esc_attr(strtolower($row->mob)); ?>"
             data-reg="<?php echo esc_attr(strtolower($row->reg)); ?>">

            <div class="att-col">
                <div><h5><strong><?php echo esc_html($row->name); ?></strong></h5></div>
                <div><h4><?php echo esc_html($row->del); ?></h4></div>
            </div>

            <div class="att-col">
                <div><h4><?php echo esc_html($row->reg); ?></h4></div>
                <div><h4><?php echo esc_html($row->mob); ?></h4></div>
            </div>

            <div class="att-col">
                <div>
                    <img src="https://66wbshpc26.trustwbhmai.in/wp-content/uploads/2026/08/ChatGPT-Image-Jul-23-2026-11_16_05-PM-768x753-removebg-preview.png" class="att-logo" alt="">
                </div>
                <div>
                <?php if ($row->present_absent): ?>
                    <button class="att-btn" style="background:red" onclick="markAbsent(<?php echo esc_js($row->id); ?>)"><b style="color:white">Mark Absent</b></button>
                    <button class="att-btn print-btn" onclick="printBadge(<?php echo esc_js($row->id); ?>)">Print</button>
                <?php else: ?>
                    <button class="att-btn" style="background:green" onclick="markPresent(<?php echo esc_js($row->id); ?>)"><b style="color:white">Mark Present</b></button>
                <?php endif; ?>
                </div>
            </div>
        </div>
    <?php endforeach; ?>
    </div>

    <script>
    const LD0801_BRIDGE = 'http://127.0.0.1:9101';
    let printQueue = [];
    let printing = false;

    document.getElementById('att-search').addEventListener('input', function(){
        const q=this.value.toLowerCase();
        document.querySelectorAll('.att-box').forEach(box=>{
            const ok=(box.dataset.name||'').includes(q) ||
                     (box.dataset.mob||'').includes(q) ||
                     (box.dataset.reg||'').includes(q);
            box.style.display=ok?'flex':'none';
        });
    });

    function sleep(ms){return new Promise(r=>setTimeout(r,ms));}

    function markPresent(id){
        fetch('<?php echo esc_url(admin_url('admin-ajax.php')); ?>',{
            method:'POST',
            headers:{'Content-Type':'application/x-www-form-urlencoded'},
            body:'action=att_mark_present&id='+encodeURIComponent(id)
        }).then(()=>location.reload());
    }

    function markAbsent(id){
        const pwd=prompt('Enter password to mark absent:');
        if(!pwd)return;
        fetch('<?php echo esc_url(admin_url('admin-ajax.php')); ?>',{
            method:'POST',
            headers:{'Content-Type':'application/x-www-form-urlencoded'},
            body:'action=att_mark_absent&id='+encodeURIComponent(id)+'&pwd='+encodeURIComponent(pwd)
        }).then(()=>location.reload());
    }

    function printBadge(id){
        printQueue.push(id);
        processQueue();
    }

    async function processQueue(){
        if(printing || !printQueue.length)return;
        printing=true;
        const id=printQueue.shift();
        const box=document.querySelector('.att-box[data-id="'+CSS.escape(String(id))+'"]');
        if(box)box.classList.add('att-printing');

        try{
            const attendee=await getAttendee(id);
            const png=await makeLabel(attendee);
            const res=await fetch(LD0801_BRIDGE+'/print',{
                method:'POST',
                headers:{'Content-Type':'application/json'},
                body:JSON.stringify({
                    reference:'Attendance '+String(attendee.reg||''),
                    content:[{
                        type:'image',
                        base64:png,
                        align:'center',
                        dither:false
                    }]
                })
            });
            const data=await res.json().catch(()=>({}));
            if(!res.ok || data.status!=='printed'){
                throw new Error(data.error || 'LD0801 bridge did not confirm printing.');
            }
        }catch(e){
            alert('Printing failed: '+e.message);
        }

        if(box)box.classList.remove('att-printing');
        printing=false;
        await sleep(150);
        processQueue();
    }

    async function getAttendee(id){
        const res=await fetch('<?php echo esc_url(admin_url('admin-ajax.php')); ?>',{
            method:'POST',
            headers:{'Content-Type':'application/x-www-form-urlencoded'},
            body:'action=att_get_attendee&id='+encodeURIComponent(id)
        });
        const data=await res.json();
        if(!data.success)throw new Error(data.data||'Attendee not found.');
        return data.data;
    }

    function loadQR(){
        return new Promise((resolve,reject)=>{
            if(typeof QRCode!=='undefined'){resolve();return;}
            const s=document.createElement('script');
            s.src='https://cdn.jsdelivr.net/npm/qrcodejs@1.0.0/qrcode.min.js';
            s.onload=resolve;
            s.onerror=()=>reject(new Error('QR library failed to load.'));
            document.head.appendChild(s);
        });
    }

    async function makeLabel(a){
        await loadQR();

        const W=400, H=240;
        const c=document.createElement('canvas');
        c.width=W;c.height=H;
        const ctx=c.getContext('2d');
        ctx.fillStyle='#fff';
        ctx.fillRect(0,0,W,H);
        ctx.fillStyle='#000';
        ctx.textAlign='left';
        ctx.textBaseline='top';

        const qrSize=105;
        const qrX=275;
        const qrY=28;

        const holder=document.createElement('div');
        holder.style.position='fixed';
        holder.style.left='-10000px';
        holder.style.top='0';
        holder.style.width=qrSize+'px';
        holder.style.height=qrSize+'px';
        holder.style.background='#fff';
        document.body.appendChild(holder);

        new QRCode(holder,{
            text:String(a.reg||''),
            width:qrSize,
            height:qrSize,
            correctLevel:QRCode.CorrectLevel.M
        });

        await sleep(100);
        let qr=holder.querySelector('canvas')||holder.querySelector('img');

        let name=String(a.name||'');
        let designation=String(a.del||'');
        let fs=name.length>32?21:(name.length>24?25:29);
        ctx.font='bold '+fs+'px Arial';
        while(name.length>1 && ctx.measureText(name).width>245) name=name.slice(0,-1);
        ctx.fillText(name,12,42);

        fs=designation.length>32?14:(designation.length>24?16:19);
        ctx.font='bold '+fs+'px Arial';
        while(designation.length>1 && ctx.measureText(designation).width>245) designation=designation.slice(0,-1);
        ctx.fillText(designation,12,82);

        if(qr)ctx.drawImage(qr,qrX,qrY,qrSize,qrSize);
        holder.remove();

        return c.toDataURL('image/png').replace(/^data:image\/png;base64,/,'');
    }
    </script>
    <?php
    return ob_get_clean();
}

add_action('wp_ajax_att_mark_present','att_mark_present_native');
add_action('wp_ajax_nopriv_att_mark_present','att_mark_present_native');
function att_mark_present_native(){
    global $wpdb;
    $id=intval($_POST['id']??0);
    if($id)$wpdb->update($wpdb->prefix.'attendance',['present_absent'=>1],['id'=>$id]);
    wp_die();
}

add_action('wp_ajax_att_mark_absent','att_mark_absent_native');
add_action('wp_ajax_nopriv_att_mark_absent','att_mark_absent_native');
function att_mark_absent_native(){
    if(($_POST['pwd']??'')!=='12345')wp_die();
    global $wpdb;
    $id=intval($_POST['id']??0);
    if($id)$wpdb->update($wpdb->prefix.'attendance',['present_absent'=>0],['id'=>$id]);
    wp_die();
}

add_action('wp_ajax_att_get_attendee','att_get_attendee_native');
add_action('wp_ajax_nopriv_att_get_attendee','att_get_attendee_native');
function att_get_attendee_native(){
    global $wpdb;
    $id=intval($_POST['id']??0);
    $row=$wpdb->get_row($wpdb->prepare(
        "SELECT * FROM {$wpdb->prefix}attendance WHERE id=%d",$id
    ));
    if(!$row)wp_send_json_error('Attendee not found.');
    wp_send_json_success([
        'id'=>(int)$row->id,
        'reg'=>(string)$row->reg,
        'name'=>(string)$row->name,
        'mob'=>(string)$row->mob,
        'del'=>(string)$row->del
    ]);
}
