// Verify that an unrelated server or stale asset version is rejected before rendering.
const http=require('node:http'),assert=require('node:assert/strict');
const {once}=require('node:events');
const {verifyService,assetDigest}=require('./preview_validation_support.cjs');
(async()=>{
  const cases=[
    {application:'another-preview',protocol:1,assetDigest:assetDigest()},
    {application:'minecraft-cosmic-material',protocol:1,assetDigest:'stale'},
    {application:'minecraft-cosmic-material',protocol:999,assetDigest:assetDigest()},
    {application:'minecraft-cosmic-material',protocol:1,lab:'fantasy',assetDigest:assetDigest()}
  ];
  for(const body of cases){
    const server=http.createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(body));});
    server.listen(0,'127.0.0.1');await once(server,'listening');
    try{await assert.rejects(verifyService(`http://127.0.0.1:${server.address().port}/`),/Wrong preview service/);}
    finally{await new Promise(resolve=>server.close(resolve));}
  }
  console.log(JSON.stringify({status:'passed',checks:cases.length,scope:'wrong application, stale assets, incompatible protocol and wrong lab are rejected'}));
})().catch(e=>{console.error(e);process.exitCode=1;});
