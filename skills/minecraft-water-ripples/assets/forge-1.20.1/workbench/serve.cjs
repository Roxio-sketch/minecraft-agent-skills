const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=__dirname,port=Number(process.argv[2]||8766);
const types={'.html':'text/html; charset=utf-8','.js':'application/javascript; charset=utf-8','.json':'application/json; charset=utf-8','.png':'image/png'};
http.createServer((req,res)=>{try{
 if(req.method==='POST'&&req.url==='/api/export'){
  // Same-origin localhost parameter export, limited to validated recipe data.
  if(req.headers.origin!==`http://127.0.0.1:${port}`&&req.headers.origin!==`http://localhost:${port}`){res.writeHead(403);res.end();return;}
  let data='';req.on('data',chunk=>{data+=chunk;if(data.length>32768)req.destroy();});
  req.on('end',()=>{try{const recipe=require('./wave-core.js').validate(JSON.parse(data));
   const dir=path.join(root,'exports');fs.mkdirSync(dir,{recursive:true});
   const name='recipe-'+Date.now()+'.json',file=path.join(dir,name);fs.writeFileSync(file,JSON.stringify(recipe,null,2)+'\n');
   res.setHeader('Content-Type','application/json');res.end(JSON.stringify({path:file,url:'/exports/'+name}));
  }catch(e){res.writeHead(400);res.end(JSON.stringify({error:e.message}));}});return;
 }
 if(req.method!=='GET'&&req.method!=='HEAD'){res.writeHead(405);res.end();return;}
 const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
 const file=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));
 if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
 res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.setHeader('Cache-Control','no-store');res.end(fs.readFileSync(file));
}catch(e){res.writeHead(404);res.end('Not found');}}).listen(port,'127.0.0.1',()=>console.log(`Workbench http://127.0.0.1:${port}`));
