package robots;

import java.awt.Color;
import java.awt.geom.*;
import robocode.*;
import robocode.util.Utils;

//CODIGO DO PROTOBOT
//Esse codigo foi utilizado pela equipe GER X na robocode 2014
//Para consultas futuras, evitar alterar este, apenas criando
//novas versões para competicoes futuras.

public class GERXProtoBot extends AdvancedRobot {
	//inicio
	public Point2D.Double myLocation;								//propria localizacao, auto explicativo
	public Point2D.Double[] enemyPoints = new Point2D.Double[30];	//enemy points e energy guardam os dados dos oponentes
	public double[] enemyEnergy = new double[30];
	public double distMark = 0;										//distancia atual, cresce quando numero de alvos cai
	public double distMarkOri = 0; 									//distancia base para marcar alvos
	int count = 0;													//auxiliar para o enemy points e energy

	//run e a funcao que roda uma vez ao iniciar a partida
	//simplificando, ao iniciar a partida "o jogo" chama "robo.run()" em todos os robos
	public void run(){
		setBodyColor(new Color(220,30,0));			//cores ajustadas em homenagem ao protoman
		setGunColor(new Color(255,255,255));		//NAO ALTERAR
		setRadarColor(new Color(164,20,0));
		setBulletColor(new Color(57,198,106));
		setScanColor(new Color(220,30,0));

		setAdjustGunForRobotTurn(true);				//faz a arma se mover independente do corpo
        setAdjustRadarForGunTurn(true);				//faz o radar se mover independente da arma
        
        //distMarkOri vai calcular qual seria a distancia "ideal" para tentar atingir um alvo com base no tamanho do campo caso so haja 1 inimigo
        distMarkOri = (double) Math.sqrt((getBattleFieldHeight()*getBattleFieldHeight()) + (getBattleFieldWidth() * getBattleFieldWidth()));
        while (true){											//distMark pega esse valor do Ori e divide pelo número de inimigos
        	distMark = (double) distMarkOri/getOthers();		//a distMark cresce com os inimigos morrendo, ate voltar pra Ori quando houver 1 inimigo
            turnRadarRightRadians(Double.POSITIVE_INFINITY);	//mantém o radar rodando todo o tempo, isso diminui um pouco a precisao dos tiros, por nao travar
            													//o radar sempre em cima de um, mas é essencial pro algoritmo de gravidade inversa
        }
	}

	//funcao chamada sempre que o radar encontra um inimigo
	//e dentro daqui que sao usados os codigos e funcoes da movimentacao e tiros
	public void onScannedRobot(ScannedRobotEvent e){
		myLocation = new Point2D.Double(getX(), getY());	//salva sua localizacao para futuras contas

		//a soma do angulo do inimigo em relacao ao robo, e o angulo do seu robo em relacao ao norte
		//resulta no angulo absoluto do inimigo em relacao ao norte, usado em contas
		double absBearin = e.getBearingRadians() + getHeadingRadians();

		//esse é o inimigo encontrado sendo adicionado aos vetores de energia e distancia
		//count incrementa a cada novo inimigo encontrado e volta pro inicio quando atingir
		//o numero de inimigos existentes, assim cada inimigo fica em uma posicao do vetor
		//sem termos de ficar necessariamente marcando eles de alguma forma mais complexa
		Point2D.Double tempEnemy = new Point2D.Double(getX()+e.getDistance()*Math.sin(absBearin),getY()+e.getDistance()*Math.cos(absBearin));
		enemyPoints[count] = tempEnemy;
		enemyEnergy[count] = e.getEnergy();
	    if(enemyPoints[count].distance(getX(),getY()) <= distMark){
	    	doTheGunning(absBearin, e.getHeadingRadians(), tempEnemy, e.getVelocity(), myLocation, definePower(e.getDistance(), distMark, getEnergy()));
	    }


		//caso encontremos mais inimigos que o numero de inimigos existentes
	    //estamos repetindo e voltamos o contador para 0, como explicado acima
		count++;
		if(count>=getOthers()){
		    count=0;
		}

		//Metodo da Gravidade Inversa
		//supondo que cada robo e parede inimiga tem uma massa, calcula-se uma "forca gravitacional"
		//que ira repelir o robo. A combinacao dessas forcas faz o robo se deslocar tentando atingir
		//um ponto de distancia otima entre paredes e inimigos.
		//alguns ajustes foram feitos pra impedir ele de achar um ponto de equilibrio e ficar parado
		//ou simplesmente ser muito previsivel. Ele tende a orbitar o ponto que gostaria de alcancar
		//ao inves de ir direto para ele
		
		//criando as forças que irao mover o robo
		double xForce = 0, yForce = 0;
		double xForceParedes = 0, yForceParedes = 0;

		//percorrendo os vetores inimigos para gerar a resultante
		//a forca e proporcional ao quadrado da distancia e a energia dos inimigos
		//a ideia e que gostaria de ficar mais proximo de inimigos com menos energia que tem portanto
		//uma capacidade menor de causar dano, e podemos tentar elimina-los logo pra ganhar vantagem
		for(int i=0;i<getOthers() && enemyPoints[i] != null;i++){
		    double absBearing = Utils.normalAbsoluteAngle(Math.atan2(enemyPoints[i].x-getX(),enemyPoints[i].y-getY()));
		    double distance = enemyPoints[i].distance(getX(),getY());
		    xForce -= Math.sin(absBearing) * enemyEnergy[i] / (distance * distance * 50);
		    yForce -= Math.cos(absBearing) * enemyEnergy[i] / (distance * distance * 50);
		}

		//adicionar a gravidade das paredes
		//elas sao consideradas inimigos com 20 de energia
		//uma possibilidade seria diminuir a energia efetiva delas mas fazer ao cubo da distancia
		//ou alterar a energia delas. A ideia e que a forca resultante nunca o deixe muito proximo
		//das paredes mas nao o jogue muito no centro. Ajuste iterativamente caso ache necessario
		xForceParedes -= (double) 20 / ((getBattleFieldWidth()-getX()) * (getBattleFieldWidth()-getX()));
		xForceParedes += (double) 20 / (getX() * getX());
		yForceParedes -= (double) 20 / ((getBattleFieldHeight()-getY()) * (getBattleFieldHeight()-getY()));
		yForceParedes += (double) 20 / (getY() * getY());

		xForce += xForceParedes;	//as forcas das paredes sao adicionadas as forcas existentes
		yForce += yForceParedes;


		//angulo no qual o robo gostaria de andar
		//se nao houve força gerada ele segue a ultima media "por inercia"
		//isso evita alguns casos que ele ficaria parado em um ponto
		//o robo tambem nao se alinha antes de ir pra direcao certa, ele vai
		//fazendo isso durante. Isso faz ele tender a orbitar o ponto de forca nula
		//e tambem diminui as vezes que ele ficaria parado

		double angle = Math.atan2(xForce, yForce);

		//andar na direção do angulo
		//o if-else e usado pra caso a forca seja pra tras dele, ele "ligue a re"
		//ao inves de virar 180 graus. Lembrando que aqui sao utilizados "set" para
		//que ele execute tudo ao mesmo tempo e tente nao ficar parado nunca
		if(xForce != 0 && yForce != 0){
			if(Math.abs(angle-getHeadingRadians())<Math.PI/2){
			    setTurnRightRadians(Utils.normalRelativeAngle(angle-getHeadingRadians()));
			    setAhead(Double.POSITIVE_INFINITY);
			}
			else {
			    setTurnRightRadians(Utils.normalRelativeAngle(angle+Math.PI-getHeadingRadians()));
			    setAhead(Double.NEGATIVE_INFINITY);
			}
		}
	}

	//retorna a força da bala baseado na distancia e vida
	//o calculo e basicamente mais energia quando estiver mais perto e com mais energia
	//aquela subtracao e pra garantir que seja sempre um resultado multiplo de 0.1 mesmo
	//que as contas deem um numero com mais casas decimais
	double definePower(double distance, double max, double energy){
		if(distance < 65) return 3;
		return Math.min(3, (3*(energy/100)*(1-(distance/max))) - ((3*(energy/100)*(1-(distance/max)))%0.1));
	}

	
	//Mira por Previsao Linear Iterativa
	//basicamente ele supoe que o inimigo vai continuar andando na mesma direcao, preve
	//a posicao futura, e vai recalculando onde deveria estar o angulo da turreta ate
	//que a bala, na velocidade e angulo iterados, acertariam o robo inimigo na posicao futura
	//existem pequenas imprecisoes relacionada ao algoritmo, movimento do proprio robo, o
	//fato do radar ficar rodando e talvez usar dados levemente desatualizados do robo inimigo
	//mas no geral funciona bem supondo que nao se tente atirar em algum inimigo muito distante
	//e por isso ha um distMark pra escolher inimigos proximos e nao gastar energia a toa
	void doTheGunning(double enemyAbsBear, double enemyFacDir, Point2D.Double enemyCoords, double enemyVel, Point2D.Double myPos, double bulPower){
		double tickT = 1;
		
		Point2D.Double futEnCoord = new Point2D.Double(enemyCoords.x, enemyCoords.y);
		
		while((tickT*(20 - 3*bulPower)) < Point2D.Double.distance(myPos.x, myPos.y, futEnCoord.x, futEnCoord.y)){
			futEnCoord.setLocation(futEnCoord.x + Math.sin(enemyFacDir)*enemyVel, futEnCoord.y + Math.cos(enemyFacDir)*enemyVel);
			//18 referentes ao tamanho de robos
			//considera as paredes na previsao linear
			if(	futEnCoord.x < 18.0 || futEnCoord.y < 18.0 || futEnCoord.x > getBattleFieldWidth() - 18.0 || futEnCoord.y > getBattleFieldHeight() - 18.0){
				futEnCoord.setLocation(Math.min(Math.max(18.0, futEnCoord.x), getBattleFieldWidth() - 18.0), Math.min(Math.max(18.0, futEnCoord.y), getBattleFieldHeight() - 18.0));
			}
				
			tickT++;
		}
		
		//mirar e atirar, aqui manda a turreta girar o angulo e em seguida atirar
		//nota que o angulo usado e a diferenca entre o heading da arma, relativo ao norte
		//e o absolute bearing que foi medido anteriormente (angulo do inimigo com o norte)
		double theta = Utils.normalAbsoluteAngle(Math.atan2(futEnCoord.x - myPos.x, futEnCoord.y - myPos.y));
		
		turnGunRightRadians(Utils.normalRelativeAngle(theta - getGunHeadingRadians()));
		fire(bulPower);
	}

}


