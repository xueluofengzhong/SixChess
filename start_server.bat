@echo off
echo ========================================
echo   SixChess (六子棋) Server Launcher
echo ========================================
echo.

REM Step 1: Start the game server
echo [1/2] Starting SixChess WebSocket server on port 8080...
start "SixChess Server" cmd /c "cd /d %~dp0server && java -jar build\libs\sixchess-server-1.0.0.jar"
echo Server started in background.

REM Wait for server to start
timeout /t 2 /nobreak >nul

REM Step 2: Start ngrok tunnel
echo [2/2] Starting ngrok tunnel...
echo.
echo ========================================
echo   Share the ngrok URL with your opponent!
echo   The URL will look like:
echo   https://xxxx.ngrok-free.app
echo ========================================
echo.
ngrok http 8080

pause