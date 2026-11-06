import subprocess,os,time,json,urllib.request,urllib.error,secrets,pathlib,tempfile,sys
root=pathlib.Path(__file__).resolve().parents[1]
temporary=tempfile.TemporaryDirectory(prefix='shortener-acceptance-')
work=pathlib.Path(temporary.name)
env=os.environ.copy();env.update(SHORTENER_API_KEY=secrets.token_hex(24),BASE_URL='http://127.0.0.1:18081',PORT='18081',DB_URL='jdbc:h2:file:'+str(work/'database')+';DB_CLOSE_ON_EXIT=FALSE')
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def call(path,data=None):
 req=urllib.request.Request(env['BASE_URL']+path,headers={'X-API-Key':env['SHORTENER_API_KEY'],'Content-Type':'application/json'},data=json.dumps(data).encode() if data else None)
 with opener.open(req,timeout=3) as res:return json.load(res)
def start(log):
 f=open(work/log,'w');p=subprocess.Popen(['java','-jar',str(root/'target/url-shortener-1.0.0.jar')],env=env,stdout=f,stderr=subprocess.STDOUT)
 for _ in range(90):
  if p.poll() is not None: raise RuntimeError('Server failed; see '+log)
  try:
   call('/actuator/health');return p,f
  except (OSError,urllib.error.URLError):time.sleep(.5)
 p.terminate();raise RuntimeError('Startup timeout')
def stop(p,f):
 p.terminate();p.wait(timeout=15);f.close()
p,f=start('server.log')
try:
 subprocess.run([sys.executable,str(root/'scripts/smoke.py')],env=env,check=True)
 subprocess.run([sys.executable,str(root/'scripts/load.py')],env=env,check=True)
 alias='persist'+secrets.token_hex(5)
 call('/api/v1/links',{'url':'https://example.com/persistence','customAlias':alias})
finally:stop(p,f)
p,f=start('restart.log')
try:
 row=call('/api/v1/links/'+alias);assert row['url']=='https://example.com/persistence'
 print('PASS: link survives packaged-server restart on file-backed H2')
finally:stop(p,f)
invalid=env.copy();invalid['SHORTENER_API_KEY']=''
with open(work/'no-key.log','w') as log:
 result=subprocess.run(['java','-jar',str(root/'target/url-shortener-1.0.0.jar')],env=invalid,stdout=log,stderr=subprocess.STDOUT,timeout=45)
 assert result.returncode!=0
assert 'SHORTENER_API_KEY must contain at least 24 characters' in (work/'no-key.log').read_text()
print('PASS: missing production key prevents startup')

temporary.cleanup()
